package servlet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.org.apache.bcel.internal.generic.NEW;

import src.coordinate.ConvertLngLatXyCoordinate;
import src.coordinate.GetLngLatOsm;
import src.db.getData.OsmRoadDataGeom;

/**
 * 伸縮する道路を描画
 * http://localhost:8080/EmmaGlueMuraseOriginal/MainServlet?type=DrawElasticRoad
 * @author murase
 *
 */
public class DrawElasticRoad {
	
	/** 地図パネルの横幅. */
	public static  int WINDOW_WIDTH = 700;
	/** 地図パネルの高さ. */
	public static  int WINDOW_HEIGHT = 700;
	/** 初期の経度. */
	private static final double DEFAULT_LNG = 136.9309671669116;	// 鶴舞公園.
	/** 初期の緯度. */
	private static final double DEFAULT_LAT = 35.15478942665804;	// 鶴舞公園.
	/** 初期の緯度経度Point2D形式 */
	private static final Point2D.Double DEFAULT_LNGLAT = new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT);
	/** 初期のスケール. */
//	private static final int DEFAULT_SCALE = 15;
	
	
	/** focusのスケール */
	private static final int FOCUS_SCALE = 17;
	/** contextのスケール */
	private static final int CONTEXT_SCALE = 15;
	/** glue内側の半径 */
	private static final int GLUE_INNER_RADIUS=50;
	/** glue外側の半径 */
	private static final int GLUE_OUTER_RADIUS=150;
	
	// 中心点からglue内側の長さ.
	public double glueInnerRadiusMeter;
	// 中心点からglue外側の長さ.
	public double glueOuterRadiusMeter;
	
	
	Graphics2D _graphics2d;
//	public GetLngLatOsm _getLngLatOsm;
//	public ConvertLngLatXyCoordinate _convert;
	/** focus */
	public GetLngLatOsm _getLngLatOsmFocus;
	/** focus領域の緯度経度xy変換 */
	public ConvertLngLatXyCoordinate _convertFocus;
	/** context */
	public GetLngLatOsm _getLngLatOsmContext;
	/** context領域の緯度経度xy変換 */
	public ConvertLngLatXyCoordinate _convertContext;
	/** glue領域の緯度経度xy変換 */
	public ArrayList<ConvertLngLatXyCoordinate> _arrayConvert;
//	public Point2D _upperLeftLngLat;
//	public Point2D _lowerRightLngLat;
	
	
	public DrawElasticRoad(HttpServletRequest request, HttpServletResponse response) {
		try{
			OutputStream out=response.getOutputStream();
			//BufferedImage img = emgd.getEmGlueImage(param);
			ImageIO.write( drawImage(), "png", out);
			
		}catch(Exception e){
			e.printStackTrace();
		}

	}
	
	
	/**
	 * 道路データの取得しbufferedimageの作成
	 * @return
	 */
	private BufferedImage drawImage(){
		BufferedImage bfImage=null;
		bfImage=new BufferedImage( WINDOW_WIDTH, WINDOW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		_graphics2d = (Graphics2D) bfImage.getGraphics();
		// アンチエイリアス設定：遅いときは次の行をコメントアウトする.
		_graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		// 中心座標とスケールから 左上と右下の座標取得.
//		_getLngLatOsm = new GetLngLatOsm(new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT), DEFAULT_SCALE, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
//		_upperLeftLngLat = _getLngLatOsm._upperLeftLngLat;
//		_lowerRightLngLat = _getLngLatOsm._lowerRightLngLat;
		// 緯度経度とXy座標の変換用インスタンス.
//		_convert = new ConvertLngLatXyCoordinate((Point2D.Double)_upperLeftLngLat,
//				(Point2D.Double)_lowerRightLngLat, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		
		//focus用の緯度経度xy変換
		_getLngLatOsmFocus = new GetLngLatOsm(new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT), FOCUS_SCALE, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		_convertFocus = new ConvertLngLatXyCoordinate((Point2D.Double)_getLngLatOsmFocus._upperLeftLngLat,
				(Point2D.Double)_getLngLatOsmFocus._lowerRightLngLat, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		//context用の緯度経度xy変換
		_getLngLatOsmContext = new GetLngLatOsm(new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT), CONTEXT_SCALE, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		_convertContext = new ConvertLngLatXyCoordinate((Point2D.Double)_getLngLatOsmContext._upperLeftLngLat,
				(Point2D.Double)_getLngLatOsmContext._lowerRightLngLat, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		glueInnerRadiusMeter = GLUE_INNER_RADIUS*_convertFocus.meterPerPixel.getX();
		glueOuterRadiusMeter = GLUE_OUTER_RADIUS*_convertContext.meterPerPixel.getX();
		// 100種類のの変換オブジェクト.
		_arrayConvert = calcArrayConvert();
		
		// 道路データの取得.
		OsmRoadDataGeom osmRoadDataGeom = new OsmRoadDataGeom();
		osmRoadDataGeom.startConnection();
		// 矩形範囲内の道路データを取得する.
		osmRoadDataGeom.insertOsmRoadData(_getLngLatOsmContext._upperLeftLngLat, _getLngLatOsmContext._lowerRightLngLat);
		osmRoadDataGeom.__arc = osmRoadDataGeom._arc;
		// 半径150pixel分の道路データを取得する.
//		osmRoadDataGeom.getOsmRoadFromPolygon(new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT), (_convert.meterPerPixel.getX() * glue_outer_radius));
		
		
		osmRoadDataGeom.endConnection();
		
		// 道路の描画.
		paintElasticRoadData(osmRoadDataGeom.__arc);
		
		_graphics2d.setColor(Color.red);
		// 中心点.
		_graphics2d.drawOval(WINDOW_WIDTH/2-2, WINDOW_WIDTH/2-2, 4, 4);
		// glue領域内側想定範囲.
		_graphics2d.drawOval(WINDOW_WIDTH/2-GLUE_INNER_RADIUS, WINDOW_WIDTH/2-GLUE_INNER_RADIUS, GLUE_INNER_RADIUS*2, GLUE_INNER_RADIUS*2);
		// glue領域外側想定範囲.
		_graphics2d.drawOval(WINDOW_WIDTH/2-GLUE_OUTER_RADIUS, WINDOW_WIDTH/2-GLUE_OUTER_RADIUS, GLUE_OUTER_RADIUS*2, GLUE_OUTER_RADIUS*2);
		
		return bfImage;
	}
	
	/**
	 * glueの内側から外側までの座標変換のオブジェクト生成(100個生成する)
	 * focusからcontextまで行くと縦横(1/2)になるとする
	 * 例:glue内側からglue外側まで100pixelあるとするとXpixel移動するとスケールはfocusスケールの2^(X/100)倍になる
	 * @return
	 */
	public ArrayList<ConvertLngLatXyCoordinate> calcArrayConvert(){
		ArrayList<ConvertLngLatXyCoordinate> arrayConvert= new ArrayList<>();
		// 端点の緯度経度を求める.
		GetLngLatOsm getLngLatOsmFocus = new GetLngLatOsm(new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT), FOCUS_SCALE, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		GetLngLatOsm getLngLatOsmContext = new GetLngLatOsm(new Point2D.Double(DEFAULT_LNG, DEFAULT_LAT), CONTEXT_SCALE, new Point(WINDOW_WIDTH, WINDOW_HEIGHT));
		// contextとglueの端点の緯度経度の差.
		Point2D.Double diffLngLat = new Point2D.Double(
				Math.abs(getLngLatOsmFocus._upperLeftLngLat.getX() - getLngLatOsmContext._upperLeftLngLat.getX()), 
				Math.abs(getLngLatOsmFocus._upperLeftLngLat.getY() - getLngLatOsmContext._upperLeftLngLat.getY())
				);
		// 緯度経度とXy座標の変換用インスタンス.
//		_convert = new ConvertLngLatXyCoordinate((Point2D.Double)getLngLatOsm._upperLeftLngLat,
//				(Point2D.Double)getLngLatOsm._lowerRightLngLat, new Point(1000, 1000));		
		for(int i=0; i<100; i++){
			arrayConvert.add(new ConvertLngLatXyCoordinate(
					new Point2D.Double(getLngLatOsmFocus._upperLeftLngLat.getX()-(diffLngLat.getX()*i/100), getLngLatOsmFocus._upperLeftLngLat.getY()+(diffLngLat.getY()*i/100)),
					new Point2D.Double(getLngLatOsmFocus._lowerRightLngLat.getX()+(diffLngLat.getX()*i/100), getLngLatOsmFocus._lowerRightLngLat.getY()-(diffLngLat.getY()*i/100)),
					new Point(WINDOW_WIDTH, WINDOW_HEIGHT)));
		}
		return arrayConvert;
	}
	
	
	/////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////
	///////////////////道路描画について//////////////////////////////////////
	/////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////
	/**
	 * 伸縮した道路データの描画.
	 * @param __arc
	 */
	public void paintElasticRoadData(ArrayList<ArrayList<Line2D>> __arc){
		OsmRoadDataGeom osmRoadDataGeom = new OsmRoadDataGeom();
		osmRoadDataGeom.startConnection();
		Point p1Xy;
		Point p2Xy;
		for(ArrayList<Line2D> arrArc : __arc){
			for(Line2D arc : arrArc){
				// 2点の緯度経度から中心までの距離(メートル)を求める.
				
				double p1Meter = osmRoadDataGeom.calcMeterLength(DEFAULT_LNGLAT, arc.getP1());
				double p2Meter = osmRoadDataGeom.calcMeterLength(DEFAULT_LNGLAT, arc.getP2());
				// p1について.
				if(p1Meter < glueInnerRadiusMeter){	// focus領域にある.
					p1Xy = _convertFocus.convertLngLatToXyCoordinate(arc.getP1());
//					continue;
				}else if ( glueInnerRadiusMeter < p1Meter && p1Meter < glueOuterRadiusMeter){// glue領域にある.
					// glue内側から見て何パーセントの位置にあるか.
					int glueRatio = (int)((p1Meter-glueInnerRadiusMeter)/(glueOuterRadiusMeter - glueInnerRadiusMeter)*100);
					p1Xy = _arrayConvert.get(glueRatio).convertLngLatToXyCoordinate(arc.getP1());
//					continue;
				}else{// context領域にある.
					p1Xy = _convertContext.convertLngLatToXyCoordinate(arc.getP1());
//					continue;
				}
				// p2について.
				if(p2Meter < glueInnerRadiusMeter){	// focus領域にある.
					p2Xy = _convertFocus.convertLngLatToXyCoordinate(arc.getP2());
//					continue;
				}else if ( glueInnerRadiusMeter < p2Meter && p2Meter < glueOuterRadiusMeter){// glue領域にある.
					// glue内側から見て何パーセントの位置にあるか.
					int glueRatio = (int)((p2Meter-glueInnerRadiusMeter)/(glueOuterRadiusMeter - glueInnerRadiusMeter)*100);
					p2Xy = _arrayConvert.get(glueRatio).convertLngLatToXyCoordinate(arc.getP2());
//					continue;
				}else{// context領域にある.
					p2Xy = _convertContext.convertLngLatToXyCoordinate(arc.getP2());
//					continue;
				}
				System.out.println("drawLine");
				paint2dLine(new Line2D.Double(p1Xy, p2Xy), Color.pink, (float)3);
			}
		}
		osmRoadDataGeom.endConnection();
	}

	// 線分の描画.
	private void paint2dLine(Line2D aLine, Color aColor, float aLineWidth){
		Line2D linkLine = aLine;
		// 線の幅.
		BasicStroke wideStroke = new BasicStroke(aLineWidth);
		_graphics2d.setStroke(wideStroke);
		_graphics2d.setPaint(aColor);
		_graphics2d.draw(linkLine);
	}


}