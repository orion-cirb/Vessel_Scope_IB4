package Vessel_Scope;

/*
 * Find gene
 * 
 * Author Philippe Mailly
 */
import Vessel_Scope_Utils.Cell;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;


public class Vessel_Scope_Main implements PlugIn {

    public static Vessel_Scope_Main instance;
    
    
    // parameters
    
    public  static String outDirResults = "";
    public  static String rootName = "";
    public  static Calibration cal = new Calibration();
    public  final double pixDepth = 0.5;
    public  Cell nucleus = new Cell(0, 0, 0, 0, 0, 0, 0, 0, 0);
    public  static BufferedWriter output_detail_Analyze;
    public  static boolean localImages = false;
    public  static String imagesFolder;
    public  static List<String> imagesFiles = new ArrayList<>();
    public static boolean dialogCancel = false;
    public static List<String> channels = new ArrayList<>();
    public static String autoBackground = "";
    public static String thMethod = "";
    public static double singleDotIntGeneX = 0, calibBgGeneX = 0, minVesselVol = 5000, maxVesselVol = Double.MAX_VALUE;
    public static int roiBgSize = 100;
    public static int removeSlice = 0;
    
       
    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        
        Vessel_Scope_JDialog dialog = new Vessel_Scope_JDialog(new Frame(), true);
        dialog.show();
        if (dialogCancel){
            IJ.showStatus(" Pluging canceled");
            return;
        }

        /* 
        * Images on local machine
        */

        if (localImages) {
            new Vessel_Scope_Local().run("");
        }
        
        /*
        Images on OMERO server
        */

        else {
            new Vessel_Scope_Omero().run("");     
        }

        IJ.showStatus("Process done");
    }
}