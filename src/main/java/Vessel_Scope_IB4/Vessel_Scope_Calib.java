/*
 * Calibration steps for RNAScope
 * Analyze background on rois and dots volume, intensity using xml file 
 * in images containing single dots
 */
package Vessel_Scope_IB4;


import Vessel_Scope_IB4_Utils.Dot;
import Vessel_Scope_IB4_Utils.Vessel_Scope_Processing;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom.Point3D;
import mcib3d.geom2.BoundingBox;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.VoxelInt;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;


/**
 *
 * @author phm
 */
public class Vessel_Scope_Calib implements PlugIn {
    
private String imageDir = "";
private String outDirResults = "";
private Calibration cal = new Calibration();   
private BufferedWriter output_dotCalib;

private Vessel_Scope_IB4_Utils.Vessel_Scope_Processing process = new Vessel_Scope_Processing();
    
    /**
     * Find pointed single dots in dotsPop population
     * @param arg 
     */
    private ArrayList<Dot> findSingleDots(ArrayList<Point3D> pts, Objects3DIntPopulation dotsPop, Objects3DIntPopulation pointedDotsPop, ImagePlus img) {
        ImageHandler imh = ImageHandler.wrap(img);
        ArrayList<Dot> dots = new ArrayList();
        int index = 0;
        for (Point3D pt : pts) {
            for (Object3DInt dotObj : dotsPop.getObjects3DInt()) {
                VoxelInt v = new VoxelInt(pt.getRoundX(), pt.getRoundY(), pt.getRoundZ(), 0);
                if (dotObj.contains(v)) {
                    double volObj = new MeasureVolume(dotObj).getVolumePix();
                    double intObj = new MeasureIntensity(dotObj, imh).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
                    BoundingBox boxObj = dotObj.getBoundingBox();
                    Dot dot = new Dot(index, (int)volObj, intObj, boxObj.zmin, boxObj.zmax, boxObj.zmax - boxObj.zmin);
                    dots.add(dot);
                    pointedDotsPop.addObject(dotObj);
                    dotsPop.removeObject(dotObj);
                }
            }
            index++;
        }
        return(dots);
    }
    
    
     /**
     * Label object
     * @param obj
     * @param img 
     * @param fontSize 
     */
    public void labelObject(Object3DInt obj, ImagePlus img, int fontSize) {
        if (IJ.isMacOSX())
            fontSize *= 3;
        
        BoundingBox bb = obj.getBoundingBox();
        int z = bb.zmin + 1;
        int x = bb.xmin;
        int y = bb.ymin;
        img.setSlice(z);
        ImageProcessor ip = img.getProcessor();
        ip.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        ip.setColor(255);
        ip.drawString(Integer.toString((int)obj.getLabel()), x, y);
        img.updateAndDraw();
    }
    
     /**
     * save images objects population
     * @param imgNuc
     * @param dotsPop
     * @param outDirResults
     * @param rootName
     */
    public void saveDotsImage (ImagePlus img, Objects3DIntPopulation dotsPop, String outDirResults, String rootName) {
        // dots green geneX, image in gray
        ImageHandler imgDots = ImageHandler.wrap(img).createSameDimensions();
        // draw dots population
        dotsPop.drawInImage(imgDots);
        for (Object3DInt obj : dotsPop.getObjects3DInt())
                labelObject(obj, imgDots.getImagePlus(), 12);
        ImagePlus[] imgColors = {null, imgDots.getImagePlus(), null, img.duplicate()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_DotsObjects.tif");
        imgDots.closeImagePlus();
    }
    
    
    
    
  @Override
    public void run(String arg) {
        try {
            imageDir = IJ.getDirectory("Choose directory containing images, roi and xml files ...");
            if (imageDir == null) {
                return;
            }
            File inDir = new File(imageDir);
            ArrayList<String> imageFile = process.findImages(imageDir, "tif");
            if (imageFile == null) {
                System.out.println("No Image found in "+imageDir);
                return;
            }
            
            int imageNum = 0; 
            for (String f : imageFile) {
                // Find tif files
                String rootName = FilenameUtils.getBaseName(f);
                String imageName = inDir+ File.separator+f;
                imageNum++;
                    
                // Write results file headers
                if (imageNum == 1) {
                    // create output folder
                    outDirResults = inDir + File.separator+ "Results"+ File.separator;
                    File outDir = new File(outDirResults);
                    if (!Files.exists(Paths.get(outDirResults))) {
                        outDir.mkdir();
                    } 
                    // write result file headers
                    FileWriter  fwAnalyze_detail = new FileWriter(outDirResults + "dotsCalibration_results.xls",false);
                    output_dotCalib = new BufferedWriter(fwAnalyze_detail);
                    // write results headers
                    output_dotCalib.write("Image Name\t#Dot\tDot Vol (pixel3)\tDot Integrated Intensity\tMean Dot Background intensity\t"
                            + "Corrected Dots Integrated Intensity\tDot Z center\tDot Z range\tMean intensity per single dot\n");
                    output_dotCalib.flush();
                }
                    
                // Find roi file name
                String roiFile = inDir+ File.separator + rootName + ".zip";
                if (!new File(roiFile).exists()) {
                    IJ.showStatus("No roi file found !") ;
                    return;
                }
                // Find dots xml file name
                String xmlFile = inDir+ File.separator + rootName + ".xml";
                if (!new File(xmlFile).exists()) {
                    IJ.showStatus("No xml file found !") ;
                    return;
                }
                
                // Open Gene reference channel
                System.out.println("Opening "+rootName+" ...");
                ImagePlus img = IJ.openImage(imageName);
                RoiManager rm = new RoiManager(false);
                rm.runCommand("Open", roiFile);
                int roiSize = rm.getCount();
                System.out.println("Rois found = "+roiSize);

                // Read dots coordinates in xml file
                ArrayList<Point3D> dotsCenter = process.readXML(xmlFile);
                System.out.println("Pointed dots found = "+dotsCenter.size());

                if (roiSize != dotsCenter.size()) {
                    System.out.println("Number of roi is different from from dots");
                    return;
                }

                // 3D dots segmentation
                Objects3DIntPopulation dotsPop = process.stardistPop(img, null);
                System.out.println("Total dots found = "+dotsPop.getNbObjects());

                // find pointed dots in dotsPop
                Objects3DIntPopulation pointedDotsPop = new Objects3DIntPopulation();

                ArrayList<Dot> dots = findSingleDots(dotsCenter, dotsPop, pointedDotsPop, img);
                System.out.println("Associated dots = "+dots.size());

                if (!dots.isEmpty()) {
                    // Save dots
                    saveDotsImage (img, pointedDotsPop, outDirResults, rootName);

                    // for all rois
                    // find background associated to dot
                    double sumCorIntDots = 0;
                    for (Dot dot : dots) {
                        Roi roi = rm.getRoi(dot.getIndex());
                        img.setRoi(roi);
                        ImagePlus imgCrop = img.crop("stack");
                        double bgDotInt = process.find_background(imgCrop, dot.getZmin(), dot.getZmax());
                        double corIntDot = dot.getIntDot() - (bgDotInt * dot.getVolDot());
                        sumCorIntDots += corIntDot;
                        // write results
                        output_dotCalib.write(rootName+"\t"+dot.getIndex()+"\t"+dot.getVolDot()+"\t"+dot.getIntDot()+"\t"+bgDotInt+"\t"+corIntDot+
                                "\t"+dot.getZCenter()+"\t"+(dot.getZmax()-dot.getZmin())+"\n");
                        output_dotCalib.flush();
                        process.closeImages(imgCrop);
                    }
                    double MeanIntDot = sumCorIntDots / rm.getCount();
                    output_dotCalib.write("\t\t\t\t\t\t\t\t"+MeanIntDot+"\n");
                }
            }
            if(new File(outDirResults + "dotsCalibration_results.xls").exists())
                output_dotCalib.close();
            IJ.showStatus("Calibration done");
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            Logger.getLogger(Vessel_Scope_Calib.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

                    
                              
}
