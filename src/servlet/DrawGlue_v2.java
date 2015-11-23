package servlet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import src.QuickSort2;
import src.coordinate.ConvertLngLatXyCoordinate;
import src.coordinate.ConvertMercatorXyCoordinate;
import src.coordinate.GetLngLatOsm;
import src.coordinate.LngLatMercatorUtility;
import src.db.getData.OsmRoadDataGeom;
import src.db.getData.OsmStrokeDataGeom;
import src.drawGlue_v2.StrokeSelectionAlgorithm_DrawGlue_v2;
import src.drawMitinariSenbetuAlgorithm.*;
import src.paint.PaintGlueRoad;

/**
 * focus,contextから一定数の重要なストロークを取り出し，描画する
 * @author murase
 *
 */
public class DrawGlue_v2 {
	
	/** 地図の大きさ */
	private Point windowSize = new Point(700, 700);
	/** 初期の緯度経度Point2D形式 */
	private  Point2D centerLngLat = new Point2D.Double(136.9309671669116, 35.15478942665804);// 鶴舞公園.
	/** focusのスケール */
	private int focusScale = 17;
	/** contextのスケール */
	private int contextScale = 15;
	/** glue内側の半径(pixel) */
	private int glueInnerRadius=200;
	/** glue外側の半径(pixel) */
	private int glueOuterRadius=300;
	
	/** 描画するストロークの数 */
	private static final int STROKE_NUM = 30;
	
	/** 道路の種類(car, bikeFoot) */
	private String roadType = "car";
	
	/** 中心点からglue内側の長さ(メートル) */
	private double glueInnerRadiusMeter;
	/** 中心点からglue外側の長さ(メートル)  */
	private double glueOuterRadiusMeter;
	
	/** 描画用 */
	Graphics2D _graphics2d;
	/** focusの端点の緯度経度を求める */
	private GetLngLatOsm _getLngLatOsmFocus;
	/** focus領域の緯度経度xy変換 */
	private ConvertLngLatXyCoordinate _convertFocus;
	/** contextの端点の緯度経度を求める */
	private GetLngLatOsm _getLngLatOsmContext;
	/** context領域の緯度経度xy変換 */
	private ConvertLngLatXyCoordinate _convertContext;
	/** メルカトル座標系xy変換 */
	private ConvertMercatorXyCoordinate _contextMercatorConvert;
	
	public DrawGlue_v2(HttpServletRequest request, HttpServletResponse response){
		
		// 必須パラメータがあるか.
		if(request.getParameter("centerLngLat")==null ||
				request.getParameter("focus_zoom_level")==null ||
				request.getParameter("context_zoom_level")==null ||
				request.getParameter("glue_inner_radius")==null ||
				request.getParameter("glue_outer_radius")==null
				){
			ErrorMsg.errorResponse(request, response, "必要なパラメータがありません");
			return;
		}
		// パラメータの受け取り.
		centerLngLat = new Point2D.Double(
				Double.parseDouble(request.getParameter("centerLngLat").split(",")[0]), 
				Double.parseDouble(request.getParameter("centerLngLat").split(",")[1]));
		focusScale = Integer.parseInt(request.getParameter("focus_zoom_level"));
		contextScale = Integer.parseInt(request.getParameter("context_zoom_level"));
		glueInnerRadius = Integer.parseInt(request.getParameter("glue_inner_radius"));
		glueOuterRadius = Integer.parseInt(request.getParameter("glue_outer_radius"));
		windowSize = new Point(glueOuterRadius*2, glueOuterRadius*2);
		roadType = request.getParameter("roadType") == null  ? "car" : request.getParameter("roadType");
		try{
			OutputStream out=response.getOutputStream();
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
		bfImage=new BufferedImage( windowSize.x, windowSize.y, BufferedImage.TYPE_INT_ARGB);
		_graphics2d = (Graphics2D) bfImage.getGraphics();
		_graphics2d.setBackground(new Color(241,238,232));	// 背景指定.
		_graphics2d.clearRect(0, 0, windowSize.x, windowSize.y);
		// アンチエイリアス設定：遅いときは次の行をコメントアウトする.
		_graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		//focus用の緯度経度xy変換
		_getLngLatOsmFocus = new GetLngLatOsm(centerLngLat, focusScale, windowSize);
		_convertFocus = new ConvertLngLatXyCoordinate((Point2D.Double)_getLngLatOsmFocus._upperLeftLngLat,
				(Point2D.Double)_getLngLatOsmFocus._lowerRightLngLat, windowSize);
		//context用の緯度経度xy変換
		_getLngLatOsmContext = new GetLngLatOsm(centerLngLat, contextScale, windowSize);
		_convertContext = new ConvertLngLatXyCoordinate((Point2D.Double)_getLngLatOsmContext._upperLeftLngLat,
				(Point2D.Double)_getLngLatOsmContext._lowerRightLngLat, windowSize);
		glueInnerRadiusMeter = glueInnerRadius*_convertFocus.meterPerPixel.getX();
		glueOuterRadiusMeter = glueOuterRadius*_convertContext.meterPerPixel.getX();
		// contextでのメルカトル座標系xy変換.
		_contextMercatorConvert = new ConvertMercatorXyCoordinate(
				LngLatMercatorUtility.ConvertLngLatToMercator((Point2D.Double)_getLngLatOsmContext._upperLeftLngLat), 
				LngLatMercatorUtility.ConvertLngLatToMercator((Point2D.Double)_getLngLatOsmContext._lowerRightLngLat), windowSize);
		
		// 描画する道路などのデータ.
		ArrayList<ArrayList<Point2D>> roadPath = new ArrayList<>();
		// その時の道路クラス.
		ArrayList<Integer> clazzList = new ArrayList<>();
		
		////////////////////////////////////////////////////////////////
		//////////////道路選別/////////////////////////
		////////////////////////////////////////////////////////////////
		StrokeSelectionAlgorithm_DrawGlue_v2 strokeSelectionAlgorithm = new StrokeSelectionAlgorithm_DrawGlue_v2(centerLngLat, glueInnerRadius, glueOuterRadius, glueInnerRadiusMeter, glueOuterRadiusMeter, _graphics2d);
		roadPath.addAll(strokeSelectionAlgorithm.roadPath);
		clazzList.addAll(strokeSelectionAlgorithm.clazzList);
		
		
		// roadPath.add().
		// clazzList.add().
//		System.out.println("道なり道路選別手法　道路"+roadPath);
//		System.out.println("道なり道路選別手法　道路クラス"+clazzList);
		///////////////////////////////////////////////////
		///////////////////////////////////////////////////
		///////////////////////////////////////////////////
		
		OsmRoadDataGeom osmRoadDataGeom = new OsmRoadDataGeom();
		osmRoadDataGeom.startConnection();
		//////////////////////////////////
		// 高速道路を取得.///////////////
		//////////////////////////////////
		osmRoadDataGeom.insertOsmRoadData(_getLngLatOsmContext._upperLeftLngLat, _getLngLatOsmContext._lowerRightLngLat, roadType, " clazz <=12");
		roadPath.addAll(osmRoadDataGeom._arc2);
		clazzList.addAll(osmRoadDataGeom._clazz);
		//////////////////////////////////
		// 鉄道データの取得.//////////////////
		//////////////////////////////////
		osmRoadDataGeom.insertOsmRoadData(_getLngLatOsmContext._upperLeftLngLat, _getLngLatOsmContext._lowerRightLngLat, "rail", "");
		roadPath.addAll(osmRoadDataGeom._arc2);
		clazzList.addAll(osmRoadDataGeom._clazz);
		osmRoadDataGeom.endConnection();

		
		// glue部分だけ描画
		PaintGlueRoad paintGlueRoad = new PaintGlueRoad(centerLngLat, focusScale, contextScale, glueInnerRadius, glueOuterRadius, glueInnerRadiusMeter, glueOuterRadiusMeter, _graphics2d, _convertFocus, _convertContext, _contextMercatorConvert);
		paintGlueRoad.paintElasticRoadData(roadPath, clazzList);
		
		BasicStroke wideStroke = new BasicStroke(3);
		_graphics2d.setStroke(wideStroke);
		// glueの枠線の描画.
		_graphics2d.setColor(Color.red);
		// 中心点.
		_graphics2d.drawOval(windowSize.x/2-2, windowSize.x/2-2, 4, 4);
		// glue領域内側想定範囲.
		_graphics2d.drawOval(windowSize.x/2-glueInnerRadius, windowSize.x/2-glueInnerRadius, glueInnerRadius*2, glueInnerRadius*2);
		// glue領域外側想定範囲.
		_graphics2d.drawOval(windowSize.x/2-glueOuterRadius, windowSize.x/2-glueOuterRadius, glueOuterRadius*2, glueOuterRadius*2);
		
		return bfImage;
	}
}