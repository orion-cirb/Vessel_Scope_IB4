package Vessel_Scope;


import static Vessel_Scope.Vessel_Scope_Main.autoBackground;
import static Vessel_Scope.Vessel_Scope_Main.cal;
import static Vessel_Scope.Vessel_Scope_Main.calibBgGeneX;
import static Vessel_Scope.Vessel_Scope_Main.channels;
import static Vessel_Scope.Vessel_Scope_Main.imagesFiles;
import static Vessel_Scope.Vessel_Scope_Main.imagesFolder;
import static Vessel_Scope.Vessel_Scope_Main.output_detail_Analyze;
import static Vessel_Scope.Vessel_Scope_Main.removeSlice;
import Vessel_Scope_Utils.Cell;
import Vessel_Scope_Utils.Vessel_Scope_Processing;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.BF;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import java.util.ArrayList;
import loci.plugins.in.ImporterOptions;
import mcib3d.geom.Objects3DPopulation;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import java.util.Collections;


/**
 *
 * @author phm
 */
public class Vessel_Scope_Local implements PlugIn {
    
private Vessel_Scope_Utils.Vessel_Scope_Processing process = new Vessel_Scope_Processing();
    
    @Override
    public void run(String arg) {
            try {
                // create output folder
                String outDirResults = imagesFolder + File.separator+ "Results"+ File.separator;
                File outDir = new File(outDirResults);
                if (!Files.exists(Paths.get(outDirResults))) {
                    outDir.mkdir();
                }
                // initialize results files
                process.InitResults(outDirResults);
                String rootName = "";
                
                // create OME-XML metadata store of the latest schema version
                ServiceFactory factory;
                factory = new ServiceFactory();
                OMEXMLService service = factory.getInstance(OMEXMLService.class);
                IMetadata meta = service.createOMEXMLMetadata();
                ImageProcessorReader reader = new ImageProcessorReader();
                reader.setMetadataStore(meta);
                for (String f : imagesFiles) {
                    rootName = FilenameUtils.getBaseName(f);
                    reader.setId(f);
                    reader.setSeries(0);
                    int sizeC = reader.getSizeC();
                    int sizeZ = reader.getSizeZ();
                    cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
                    cal.pixelHeight = cal.pixelWidth;
                    String channelsID = meta.getImageName(0);
                    String[] chs = channelsID.replace("_", "-").split("/");
                    ImporterOptions options = new ImporterOptions();
                    options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                    options.setId(f);
                    options.setSplitChannels(true);
                    options.setZBegin(0, removeSlice);
                    if (2 * removeSlice < sizeZ)
                        options.setZEnd(0, sizeZ-1  - removeSlice);
                    options.setZStep(0, 1);

                    options.setQuiet(true);

                    /*
                    * Open Channel 1 (gene X)
                    */
                    int channelIndex = ArrayUtils.indexOf(chs, channels.get(1));
                    System.out.println("-- Opening gene X channel : " + channels.get(1));
                    ImagePlus imgGeneX = BF.openImagePlus(options)[channelIndex];

                    Roi roiGeneX = null;

                    // Background detection methods

                    switch (autoBackground) {
                        // from rois
                        case "From rois" :
                            String roiFile = imagesFolder+ File.separator + rootName + ".zip";
                            if (!new File(roiFile).exists()) {
                                IJ.showStatus("No roi file found !");
                                return;
                            }
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
                    
                    // Check if rois file exist, keep rois to clear regions containing "artefacts"
                    String roiFile = imagesFolder+ File.separator + rootName + ".zip";
                    ArrayList<Roi> rois = new ArrayList<>();
                            
                    if (new File(roiFile).exists()) {
                        // Find rois
                        RoiManager rm = new RoiManager(false);
                        rm.runCommand("Open", roiFile);
                        Collections.addAll(rois, rm.getRoisAsArray());
                    }

                    //Find gene X dots
                    Objects3DPopulation geneXDots = process.findGenePop(imgGeneX, rois);
                    System.out.println("Finding gene "+geneXDots.getNbObjects()+" X dots");

                    /*
                    * Open Vessel channel
                    */
                    channelIndex = ArrayUtils.indexOf(chs, channels.get(0));
                    System.out.println("-- Opening Vessel channel : "+ channels.get(0));
                    ImagePlus imgVessel = BF.openImagePlus(options)[channelIndex];

                    // if no dilatation find cells with cellOutliner on gene reference image
                    // else dilate nucleus

                    Objects3DPopulation cellsPop = process.findVessel(imgVessel, rois);

                    // Find cells parameters in geneX images
                    ArrayList<Cell> listCells = process.tagsCells(cellsPop, geneXDots, imgGeneX, roiGeneX);

                    // write results for each cell population
                    for (int n = 0; n < listCells.size(); n++) {
                        output_detail_Analyze.write(rootName+"\t"+listCells.get(n).getIndex()+"\t"+listCells.get(n).getCellVol()+"\t"+listCells.get(n).getzCell()
                                +"\t"+listCells.get(n).getCellGeneXInt()+"\t"+listCells.get(n).getCellGeneXBgInt()+"\t"+listCells.get(n).getnbGeneXDotsCellInt()
                                +"\t"+listCells.get(n).getGeneXDotsVol()+"\t"+listCells.get(n).getGeneXDotsInt()+"\t"+listCells.get(n).getnbGeneXDotsSegInt()+"\n");
                        output_detail_Analyze.flush();                       

                    }
                    
                    // Save labelled vessel
                    process.saveCellsLabelledImage(imgVessel, cellsPop, geneXDots, imgGeneX, outDirResults, rootName);

                    // save random color vessel population
                    process.saveCells(imgVessel, cellsPop, outDirResults, rootName);

                    // save dots segmented objects
                    process.saveDotsImage (imgVessel, cellsPop, geneXDots, outDirResults, rootName);

                    process.closeImages(imgVessel);
                    process.closeImages(imgGeneX);
                }
                if (new File(outDirResults + "detailed_results.xls").exists())
                    output_detail_Analyze.close();
                } catch (IOException | DependencyException | ServiceException | FormatException ex) {
                    Logger.getLogger(Vessel_Scope_Local.class.getName()).log(Level.SEVERE, null, ex);
                }

            IJ.showStatus("Process done ...");
        }
    
    
}
