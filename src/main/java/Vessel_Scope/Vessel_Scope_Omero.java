/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vessel_Scope;


import static Vessel_Scope.Vessel_Scope_JDialog.imageData;
import static Vessel_Scope.Vessel_Scope_JDialog.selectedDataset;
import static Vessel_Scope.Vessel_Scope_JDialog.selectedProject;
import static Vessel_Scope.Vessel_Scope_Main.autoBackground;
import static Vessel_Scope.Vessel_Scope_Main.calibBgGeneX;
import static Vessel_Scope.Vessel_Scope_Main.output_detail_Analyze;
import static Vessel_Scope.Vessel_Scope_Main.removeSlice;
import static Vessel_Scope.Vessel_Scope_Main.rootName;
import Vessel_Scope_Utils.Cell;
import Vessel_Scope_Utils.OmeroConnect;
import static Vessel_Scope_Utils.OmeroConnect.addImageToDataset;
import static Vessel_Scope_Utils.OmeroConnect.getFileAnnotations;
import static Vessel_Scope_Utils.OmeroConnect.addFileAnnotation;
import static Vessel_Scope_Utils.OmeroConnect.gateway;
import static Vessel_Scope_Utils.OmeroConnect.getImageZ;
import static Vessel_Scope_Utils.OmeroConnect.securityContext;
import Vessel_Scope_Utils.Vessel_Scope_Processing;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom.Objects3DPopulation;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.MetadataFacility;
import omero.gateway.model.ChannelData;
import omero.gateway.model.FileAnnotationData;
import omero.gateway.model.ImageData;
import omero.gateway.model.PixelsData;
import org.apache.commons.lang.ArrayUtils;
import org.xml.sax.SAXException;

/**
 *
 * @author phm
 */

 // Images on OMERO server

public class Vessel_Scope_Omero implements PlugIn {
    

    private String tempDir = System.getProperty("java.io.tmpdir");
    private String outDirResults = tempDir+File.separator+"resulst.xls";
    
    private Vessel_Scope_Utils.Vessel_Scope_Processing process = new Vessel_Scope_Processing();

    
    
    
    @Override
    public void run(String arg) {
        try {
            ArrayList<String> ch = new ArrayList();
            // initialize results files
            process.InitResults(outDirResults);
            
            for (ImageData image : imageData) {
                if (image.getName().endsWith(".nd")) {
                    rootName = image.getName().replace(".nd", "");
                    PixelsData pixels = image.getDefaultPixels();
                    int sizeZ = pixels.getSizeZ();
                    int sizeC = pixels.getSizeC();
                    MetadataFacility mf = gateway.getFacility(MetadataFacility.class);
                    String[] channels = new String[sizeC];
                    for(ChannelData chs : mf.getChannelData(securityContext, image.getId())) {
                        channels[chs.getIndex()] = chs.getChannelLabeling();
                    }

                    try {                        
                        int zStart = removeSlice;
                        int zStop = (sizeZ - 2 * removeSlice) <= 0 ? sizeZ : sizeZ - removeSlice;
                        
                        /*
                        * Open Channel 1 (gene X)
                        */
                        int channelIndex = ArrayUtils.indexOf(channels, ch.get(1));
                        System.out.println("-- Opening gene X channel : " + ch.get(1));
                        ImagePlus imgGeneX = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();
                        
                        // test if rois exist
                        List<omero.model.Roi> rois = OmeroConnect.getImageRois(image);

                        // Find gene X dots
                        Objects3DPopulation geneXDots = process.findGenePop(imgGeneX, null);
                        System.out.println(geneXDots.getNbObjects() + " gene dots X found");
                        
                        // find background from roi
                        Roi roiGeneX = null;
                        
                        // Background detection methods
                        
                        switch (autoBackground) {
                            // from rois
                            case "From roi" :
                                if (image.getAnnotations().isEmpty()) {
                                    IJ.showStatus("No roi file found !");
                                    return;
                                }
                                List<FileAnnotationData> fileAnnotations = getFileAnnotations(image, null);
                                // If exists roi in image
                                String roiFile = rootName + ".zip";
                                // Find roi for gene ref and gene X
                                RoiManager rm = new RoiManager(false);
                                rm.runCommand("Open", roiFile);

                                for (int r = 0; r < rm.getCount(); r++) {
                                    roiGeneX = rm.getRoi(r);
                                }
                                break;
                            // automatic search roi from calibration values     
                            case "Auto" :
                                roiGeneX = process.findRoiBackgroundAuto(imgGeneX, calibBgGeneX);
                                break;
                            case "From calibration" :
                                roiGeneX = null;
                                break;
                        }

                        /*
                        * Open Vessel channel
                        */
                        channelIndex = ArrayUtils.indexOf(channels, ch.get(0));
                        System.out.println("-- Opening Vessel channel : "+ ch.get(0));
                        ImagePlus imgVessel = getImageZ(image, 1, channelIndex + 1, zStart, zStop).getImagePlus();


                        Objects3DPopulation cellsPop = new Objects3DPopulation();
                        cellsPop = process.findVessel(imgVessel, null);

                        // Find cells parameters in geneRef and geneX images
                        ArrayList<Cell> listCells = process.tagsCells(cellsPop, geneXDots, imgGeneX, roiGeneX);


                        // write results for each cell population
                        for (int n = 0; n < listCells.size(); n++) {
                            output_detail_Analyze.write(rootName+"\t"+listCells.get(n).getIndex()+"\t"+listCells.get(n).getCellVol()+"\t"+listCells.get(n).getzCell()
                                    +"\t"+listCells.get(n).getCellGeneXInt()+"\t"+listCells.get(n).getCellGeneXBgInt()+"\t"+listCells.get(n).getnbGeneXDotsCellInt()
                                    +"\t"+listCells.get(n).getGeneXDotsVol()+"\t"+listCells.get(n).getGeneXDotsInt()+"\t"+listCells.get(n).getnbGeneXDotsSegInt()+"\n");
                            output_detail_Analyze.flush();                       

                        }

                        // Save labelled nucleus
                        process.saveCellsLabelledImage(imgVessel, cellsPop, geneXDots, imgGeneX, outDirResults, rootName);

                        // import  to Omero server
                        addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_Objects.tif", true);
                        new File(outDirResults + rootName + "_Objects.tif").delete();

                        // save random color nucleus popualation
                        process.saveCells(imgVessel, cellsPop, outDirResults, rootName);

                        // import to Omero server
                        addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_Nucleus-ColorObjects.tif", true);
                        new File(outDirResults + rootName + "_Nucleus-ColorObjects.tif").delete();
                        
                        // save dots segmentations
                        process.saveDotsImage (imgVessel, cellsPop, geneXDots, outDirResults, rootName);
                        
                        // import to Omero server
                        addImageToDataset(selectedProject, selectedDataset, outDirResults, rootName + "_DotsObjects.tif", true);
                        new File(outDirResults + rootName + "_DotsObjects.tif").delete();

                        process.closeImages(imgVessel);
                        process.closeImages(imgGeneX);
                        

                    } catch (DSOutOfServiceException | ExecutionException | DSAccessException | ParserConfigurationException | SAXException | IOException ex) {
                        Logger.getLogger(Vessel_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (Exception ex) {
                        Logger.getLogger(Vessel_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            if (new File(outDirResults + "detailed_results.xls").exists())
                output_detail_Analyze.close();
            
            // Attach results file to image
            File fileResults = new File(outDirResults);
            addFileAnnotation(imageData.get(0), fileResults, "text/csv", "Results");
            fileResults.delete();
        } catch (ExecutionException | DSAccessException | DSOutOfServiceException | IOException ex) {
            Logger.getLogger(Vessel_Scope_Omero.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
}
