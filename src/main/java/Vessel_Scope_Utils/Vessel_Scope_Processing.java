package Vessel_Scope_Utils;



import Vessel_Scope.Vessel_Scope_Main;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.Duplicator;
import ij.plugin.GaussianBlur3D;
import ij.plugin.RGBStackMerge;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;
import mcib3d.geom.Point3D;
import mcib3d.image3d.ImageFloat;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import mcib3d.image3d.ImageLabeller;
import mcib3d.image3d.distanceMap3d.EDT;
import mcib3d.image3d.processing.FastFilters3D;
import mcib3d.image3d.regionGrowing.Watershed3D;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



/**
 *
 * @author phm
 */

public class Vessel_Scope_Processing {
    
    private CLIJ2 clij2 = CLIJ2.getInstance();
    private Vessel_Scope.Vessel_Scope_Main main = new Vessel_Scope_Main();
    
    
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
   
    
    /**
     *
     * @param img
     */
    public void closeImages(ImagePlus img) {
        img.flush();
        img.close();
    }

    
  /**
     * return objects population in an binary image
     * Using CLIJ2
     * @param imgCL
     * @return pop
     */

    private Objects3DPopulation getPopFromClearBuffer(ClearCLBuffer imgCL) {
        ClearCLBuffer output = clij2.create(imgCL);
        clij2.connectedComponentsLabelingBox(imgCL, output);
        clij2.release(imgCL);
        ImagePlus imgLab  = clij2.pull(output);
        imgLab.setCalibration(main.cal);
        ImageInt labels = new ImageLabeller().getLabels(ImageHandler.wrap(imgLab));
        Objects3DPopulation pop = new Objects3DPopulation(labels);
        clij2.release(output);
        return pop;
    }  
    
    
    /**
     * gaussian 3D filter 
     * Using CLIJ2
     * @param imgCL
     * @param sizeX
     * @param sizeY
     * @param sizeZ
     * @return imgOut
     */
 
    public ClearCLBuffer gaussianBlur3D(ClearCLBuffer imgCL, double sizeX, double sizeY, double sizeZ) {
        ClearCLBuffer imgOut = clij2.create(imgCL);
        clij2.gaussianBlur3D(imgCL, imgOut, sizeX, sizeY, sizeZ);
        clij2.release(imgCL);
        return(imgOut);
    }
    
     /**  
     * median 3D box filter
     * Using CLIJ2
     * @param imgCL
     * @param sizeX
     * @param sizeY
     * @param sizeZ
     * @return imgOut
     */ 
    public ClearCLBuffer medianFilter(ClearCLBuffer imgCL, double sizeX, double sizeY, double sizeZ) {
        ClearCLBuffer imgOut = clij2.create(imgCL);
        clij2.median3DBox(imgCL, imgOut, sizeX, sizeY, sizeZ);
        clij2.release(imgCL);
        return(imgOut);
    }
    
    /**
     * Difference of Gaussians 
     * Using CLIJ2
     * @param imgCL
     * @param sizeX1
     * @param sizeY1
     * @param sizeZ1
     * @param sizeX2
     * @param sizeY2
     * @param sizeZ2
     * @return imgGauss
     */ 
    public ClearCLBuffer DOG(ClearCLBuffer imgCL, double sizeX1, double sizeY1, double sizeZ1, double sizeX2, double sizeY2, double sizeZ2) {
        ClearCLBuffer imgCLDOG = clij2.create(imgCL);
        clij2.differenceOfGaussian3D(imgCL, imgCLDOG, sizeX1, sizeY1, sizeZ1, sizeX2, sizeY2, sizeZ2);
        clij2.release(imgCL);
        return(imgCLDOG);
    }
    
    /**
     * Fill hole
     * USING CLIJ2
     */
    private void fillHole(ClearCLBuffer imgCL) {
        long[] dims = clij2.getDimensions(imgCL);
        ClearCLBuffer slice = clij2.create(dims[0], dims[1]);
        ClearCLBuffer slice_filled = clij2.create(slice);
        for (int z = 0; z < dims[2]; z++) {
            clij2.copySlice(imgCL, slice, z);
            clij2.binaryFillHoles(slice, slice_filled);
            clij2.copySlice(slice_filled, imgCL, z);
        }
        clij2.release(imgCL);
        clij2.release(slice);
        clij2.release(slice_filled);
    }
    
  /**
   * Open
   * USING CLIJ2
   * @param imgCL
   * @return imgCLOut
   */
    private ClearCLBuffer open(ClearCLBuffer imgCL) {
        ClearCLBuffer imgCLOut = clij2.create(imgCL);
        clij2.openingBox(imgCL, imgCLOut, 1);
        clij2.release(imgCL);
        return(imgCLOut);
    }
    
    /**
     * Threshold 
     * USING CLIJ2
     * @param imgCL
     * @param thMed
     * @param fill 
     */
    public ClearCLBuffer threshold(ClearCLBuffer imgCL, String thMed, boolean fill) {
        ClearCLBuffer imgCLBin = clij2.create(imgCL);
        clij2.automaticThreshold(imgCL, imgCLBin, thMed);
        if (fill)
            fillHole(imgCLBin);
        clij2.release(imgCL);
        return(imgCLBin);
    }
    
    /**
     * Fill rois with zero
     */
    private ClearCLBuffer fillImg(ClearCLBuffer imgCL, ArrayList<Roi> rois) {
        ImagePlus img = clij2.pull(imgCL);
        img.getProcessor().setColor(Color.BLACK);
        for (int s = 1; s <= img.getNSlices(); s++) {
            img.setSlice(s);
            for (Roi r : rois) {
                img.setRoi(r);
                img.getProcessor().fill(img.getRoi());
            }
        }
        img.deleteRoi();
        ClearCLBuffer imgCLOut = clij2.push(img);
        closeImages(img);
        clij2.release(imgCL);
        return(imgCLOut);
    } 
    
    
    /**
     * Ib4cyte Cell
     * 
     */
    private Objects3DPopulation detectIb4(ImagePlus imgVessel, ArrayList<Roi> rois) {
        ImagePlus img = new Duplicator().run(imgVessel);
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLMed = medianFilter(imgCL, 2, 2, 2);
        ClearCLBuffer imgCLBin = threshold(imgCLMed, main.thMethod, false);
        Objects3DPopulation ib4Pop = new Objects3DPopulation();
        if (!rois.isEmpty()) {
            ClearCLBuffer fillImg = fillImg(imgCLBin, rois);
            ib4Pop = getPopFromClearBuffer(fillImg);
            clij2.release(fillImg);
        }
        else 
            ib4Pop = getPopFromClearBuffer(imgCLBin);
        clij2.release(imgCLBin);
        img.close();
        return(ib4Pop);
    }
    

    /**
     * Find gene population
     * @param imgGeneRef
     * @return genePop
     */
    public Objects3DPopulation findGenePop(ImagePlus imgGeneRef, ArrayList<Roi> rois) {
        ImagePlus img = new Duplicator().run(imgGeneRef);
        ClearCLBuffer imgCL = clij2.push(img);
        ClearCLBuffer imgCLMed = medianFilter(imgCL, 1, 1, 1);
        ClearCLBuffer imgCLDOG = DOG(imgCLMed, 1, 1, 1, 2, 2, 2);
        ClearCLBuffer imgCLBin = threshold(imgCLDOG, "Triangle", false); 
        Objects3DPopulation genePop = new Objects3DPopulation();
        if (rois != null) {
            ClearCLBuffer fillImg = fillImg(imgCLBin, rois);
            genePop = getPopFromClearBuffer(fillImg);
            clij2.release(fillImg);
        }
        else 
            genePop = getPopFromClearBuffer(imgCLBin);
        clij2.release(imgCLBin);
        return(genePop);
    }
    
    
    /**
     * ramdom color nucleus population
     */
    public ImagePlus colorPop (Objects3DPopulation cellsPop,  ImagePlus img) {
        //create image objects population
        Font tagFont = new Font("SansSerif", Font.PLAIN, 30);
        ImageHandler imgObj = ImageInt.wrap(img).createSameDimensions();
        imgObj.setCalibration(img.getCalibration());
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            int color = (int)(Math.random() * (255 - 1 + 1) + 1);
            Object3D obj = cellsPop.getObject(i);
            obj.draw(imgObj, color);
            String name = Integer.toString(i+1);
            int[] box = obj.getBoundingBox();
            int z = (int)obj.getCenterZ();
            int x = box[0] - 1;
            int y = box[2] - 1;
            imgObj.getImagePlus().setSlice(z+1);
            ImageProcessor ip = imgObj.getImagePlus().getProcessor();
            ip.setFont(tagFont);
            ip.setColor(color);
            ip.drawString(name, x, y);
            imgObj.getImagePlus().updateAndDraw();
        } 
        return(imgObj.getImagePlus());
    } 
    
    /**
     * Tags cell with gene spot, Integrated intensity and max spot Integrated intensty ....
     * @param cellsPop (Vessel dilated population)
     * @param dotsXPop gene population
     * @param roiBgGeneX
     * @param imgGeneX
     */
    
    public ArrayList<Cell> tagsCells(Objects3DPopulation cellsPop, Objects3DPopulation dotsXPop, ImagePlus imgGeneX, Roi roiBgGeneX) {
        
        IJ.showStatus("Finding cells with gene reference ...");
        ArrayList<Cell> cells = new ArrayList<>();
        ImageHandler imhX = ImageHandler.wrap(imgGeneX);
        int index = 0;
        
        ImagePlus imgGeneXCrop = new ImagePlus();
        double bgGeneX = 0;
        
        // crop image for background
        if (roiBgGeneX != null) {
            imgGeneX.setRoi(roiBgGeneX);
            imgGeneXCrop = imgGeneX.crop("stack");
        }
       
        
        for (int i = 0; i < cellsPop.getNbObjects(); i++) {
            double geneXDotsVol = 0, geneXDotsInt = 0;
            
            // calculate cell parameters
            index++;
            Object3D cellObj = cellsPop.getObject(i);
            double zCell = cellObj.getCenterZ();
            double cellVol = cellObj.getVolumePixels();
            double cellGeneXInt = cellObj.getIntegratedDensity(imhX);
            
            int cellMinZ = cellObj.getZmin() == 0 ? 1 : cellObj.getZmin();
            int cellMaxZ = cellObj.getZmax() > imgGeneX.getNSlices() ? imgGeneX.getNSlices() : cellObj.getZmax();
            
            
            // Cell background
            if (roiBgGeneX != null) {
                bgGeneX = find_background(imgGeneXCrop, cellMinZ, cellMaxZ);
            }
            else {
                bgGeneX = main.calibBgGeneX;
            } 
            //System.out.println("Mean Background  ref = " + bgGeneRef + " zmin "+cellMinZ+" zmax "+cellMaxZ);
            //System.out.println("Mean Background  X = " + bgGeneX);
            
            
            // X dots parameters
            for (int n = 0; n < dotsXPop.getNbObjects(); n++) {
                Object3D dotObj = dotsXPop.getObject(n);
                // find dots inside cell
                if (dotObj.hasOneVoxelColoc(cellObj)) {
                    geneXDotsVol += dotObj.getVolumePixels();
                    geneXDotsInt += dotObj.getIntegratedDensity(imhX);
                }
            }
            // dots number based on cell intensity
            int nbGeneXDotsCellInt = Math.round((float)((cellGeneXInt - bgGeneX * cellVol) / main.singleDotIntGeneX));
            
            // dots number based on dots segmented intensity
            int nbGeneXDotsSegInt = Math.round((float)((geneXDotsInt - bgGeneX * geneXDotsVol) / main.singleDotIntGeneX));
            
            Cell cell = new Cell(index, cellVol, zCell, cellGeneXInt,
                    bgGeneX, geneXDotsVol, geneXDotsInt, nbGeneXDotsCellInt, nbGeneXDotsSegInt);
            cells.add(cell);
        }
        closeImages(imgGeneXCrop);
        return(cells);
    }
    
    
    
    public  Objects3DPopulation findVessel(ImagePlus imgVessel, ArrayList<Roi> rois) {
        Objects3DPopulation VesselPopOrg = new Objects3DPopulation();
        VesselPopOrg = detectIb4(imgVessel, rois);
        System.out.println("-- Total Vessel Population :"+VesselPopOrg.getNbObjects());
        // size filter
        Objects3DPopulation cellsPop = new Objects3DPopulation(VesselPopOrg.getObjectsWithinVolume(main.minVesselVol, main.maxVesselVol, false));
        int nbCellPop = cellsPop.getNbObjects();
        System.out.println("-- Total Vessel Population after size filter: "+ nbCellPop);
        return(cellsPop);
    }
    

    private ImagePlus WatershedSplit(ImagePlus binaryMask, float rad) {
        float resXY = 1;
        float resZ = 1;
        float radXY = rad;
        float radZ = rad;
        Calibration cal = binaryMask.getCalibration();
        if (cal != null) {
            resXY = (float) cal.pixelWidth;
            resZ = (float) cal.pixelDepth;
            radZ = radXY * (resXY / resZ);
        }
        ImageInt imgMask = ImageInt.wrap(binaryMask);
        ImageFloat edt = EDT.run(imgMask, 0, resXY, resZ, false, 0);
        ImageHandler edt16 = edt.convertToShort(true);
        ImagePlus edt16Plus = edt16.getImagePlus();
        GaussianBlur3D.blur(edt16Plus, 2.0, 2.0, 2.0);
        edt16 = ImageInt.wrap(edt16Plus);
        edt16.intersectMask(imgMask);
        // seeds
        ImageHandler seedsImg = FastFilters3D.filterImage(edt16, FastFilters3D.MAXLOCAL, radXY, radXY, radZ, 0, false);
        Watershed3D water = new Watershed3D(edt16, seedsImg, 0, 0);
        water.setLabelSeeds(true);
        return(water.getWatershedImage3D().getImagePlus());
    }
    

    
    /**
     * Find min background roi
     * @param img
     * @param size
     * @return 
     */
    public Roi findRoiBackgroundAuto(ImagePlus img, double bgGene) {
        // scroll gene image and measure bg intensity in roi 
        // take roi at intensity nearest from bgGene
        
        ArrayList<RoiBg> intBgFound = new ArrayList<RoiBg>();
        
        for (int x = 0; x < img.getWidth() - main.roiBgSize; x += main.roiBgSize) {
            for (int y = 0; y < img.getHeight() - main.roiBgSize; y += main.roiBgSize) {
                Roi roi = new Roi(x, y, main.roiBgSize, main.roiBgSize);
                img.setRoi(roi);
                ImagePlus imgCrop = img.crop("stack");
                double bg = find_background(imgCrop, 1, img.getNSlices());
                intBgFound.add(new RoiBg(roi, bg));
                closeImages(imgCrop);
            }
        }
        img.deleteRoi();
        // sort RoiBg on bg value
        intBgFound.sort(Comparator.comparing(RoiBg::getBgInt));
        
        // Find nearest value from bgGene
        double min = Double.MAX_VALUE;
        double closest = bgGene;
        Roi roiBg = null;
        for (RoiBg v : intBgFound) {
            final double diff = Math.abs(v.getBgInt() - bgGene);
            if (diff < min) {
                min = diff;
                closest = v.getBgInt();
                roiBg = v.getRoi();
            }
        }
        int roiCenterX = roiBg.getBounds().x+(main.roiBgSize/2);
        int roiCenterY = roiBg.getBounds().y+(main.roiBgSize/2);
        System.out.println("Roi auto background found = "+closest+" center x = "+roiCenterX+", y = "+roiCenterY);
        return(roiBg);
    }
    
    
    /*
    * Get Mean of intensity in stack
    */
    public double find_background(ImagePlus img, int zMin, int zMax) {
        ResultsTable rt = new ResultsTable();
        Analyzer ana = new Analyzer(img, Measurements.INTEGRATED_DENSITY, rt);
        double intDen = 0;
        int index = 0;
        for (int z = zMin; z <= zMax; z++) {
            img.setSlice(z);
            ana.measure();
            intDen += rt.getValue("RawIntDen", index);
            index++;
        }
        double vol = img.getWidth() * img.getHeight() * (zMax - zMin + 1);
        double bgInt = intDen / vol;
        rt.reset();
        return(bgInt);  
    }
    
    
    /**
     * Label object
     * @param popObj
     * @param img 
     */
    public void labelsObject (Objects3DPopulation popObj, ImagePlus img, int fontSize) {
        Font tagFont = new Font("SansSerif", Font.PLAIN, fontSize);
        String name;
        for (int n = 0; n < popObj.getNbObjects(); n++) {
            Object3D obj = popObj.getObject(n);
            name = Integer.toString(n+1);
            int[] box = obj.getBoundingBox();
            int z = (int)obj.getCenterZ();
            int x = box[0] - 1;
            int y = box[2] - 1;
            img.setSlice(z+1);
            ImageProcessor ip = img.getProcessor();
            ip.setFont(tagFont);
            ip.setColor(255);
            ip.drawString(name, x, y);
            img.updateAndDraw();
        }
    }
    
    /**
     * 
     * @param xmlFile
     * @return
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException 
     */
    public ArrayList<Point3D> readXML(String xmlFile) throws ParserConfigurationException, SAXException, IOException {
        ArrayList<Point3D> ptList = new ArrayList<>();
        double x = 0, y = 0 ,z = 0;
        File fXmlFile = new File(xmlFile);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();
        NodeList nList = doc.getElementsByTagName("Marker");
        for (int n = 0; n < nList.getLength(); n++) {
            Node nNode = nList.item(n);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) nNode;
                x = Double.parseDouble(eElement.getElementsByTagName("MarkerX").item(0).getTextContent());
                y = Double.parseDouble(eElement.getElementsByTagName("MarkerY").item(0).getTextContent());
                z = Double.parseDouble(eElement.getElementsByTagName("MarkerZ").item(0).getTextContent());
            }
            Point3D pt = new Point3D(x, y, z);
            ptList.add(pt);
        }
        return(ptList);
    }
    
    public void InitResults(String outDirResults) throws IOException {
        // initialize results files
        // Detailed results
        FileWriter  fwAnalyze_detail = new FileWriter(outDirResults + main.autoBackground +"_results.xls",false);
        main.output_detail_Analyze = new BufferedWriter(fwAnalyze_detail);
        // write results headers
        main.output_detail_Analyze.write("Image Name\t#Cell\tCell Vol (pixel3)\tCell Z center\tCell Integrated intensity in gene X channel\tMean background intensity in X channel\t"
                + "Total dots gene X (based on cell intensity)\tDots X volume (pixel3)\tIntegrated intensity of dots X channel\tTotal dots gene X (based on dots seg intensity)\n");
        main.output_detail_Analyze.flush();
    }
    
    /**
     * Save nucleus with random colors
     * @param imgVessel
     * @param cellsPop
     * @param outDirResults
     * @param rootName
     */
    public void saveCells (ImagePlus imgVessel, Objects3DPopulation cellsPop, String outDirResults, String rootName) {
        ImagePlus imgColorPop = colorPop (cellsPop, imgVessel);
        IJ.run(imgColorPop, "3-3-2 RGB", "");
        FileSaver ImgColorObjectsFile = new FileSaver(imgColorPop);
        ImgColorObjectsFile.saveAsTiff(outDirResults + rootName + "_Cells-ColorObjects.tif");
        closeImages(imgColorPop);
    }
    
    
    /**
     * save images objects population
     * @param imgVessel
     * @param cellsPop
     * @param imgGeneX
     * @param outDirResults
     * @param rootName
     */
    public void saveCellsLabelledImage (ImagePlus imgVessel, Objects3DPopulation cellsPop, Objects3DPopulation dotsPop, ImagePlus imgGeneX,
            String outDirResults, String rootName) {
        // red geneRef , green geneX, blue nucDilpop
        ImageHandler imgCells = ImageHandler.wrap(imgVessel).createSameDimensions();
        ImageHandler imgDots = ImageHandler.wrap(imgVessel).createSameDimensions();
        ImagePlus imgCellLabels = ImageHandler.wrap(imgVessel).createSameDimensions().getImagePlus();
        // draw nucleus population
        cellsPop.draw(imgCells, 255);
        labelsObject(cellsPop, imgCellLabels, 24);
        // draw dots population
        dotsPop.draw(imgDots, 255);
        ImagePlus[] imgColors = {imgCells.getImagePlus(), imgDots.getImagePlus(), null, imgGeneX, null, null, imgCellLabels};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(main.cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_Objects.tif");
        imgCells.closeImagePlus();
        closeImages(imgCellLabels);
    }
    
    /**
     * save images objects population
     * @param imgVessel
     * @param cellsPop
     * @param geneXPop
     * @param outDirResults
     * @param rootName
     */
    public void saveDotsImage (ImagePlus imgVessel, Objects3DPopulation cellsPop, Objects3DPopulation geneXPop,
            String outDirResults, String rootName) {
        // red dots geneRef , dots green geneX, blue nucDilpop
        ImageHandler imgCells = ImageHandler.wrap(imgVessel).createSameDimensions();
        ImageHandler imgDotsGeneX = ImageHandler.wrap(imgVessel).createSameDimensions();
        ImageHandler imgCellNumbers = ImageHandler.wrap(imgVessel).createSameDimensions();
        // draw nucleus dots population
        cellsPop.draw(imgCells, 255);
        labelsObject(cellsPop, imgCellNumbers.getImagePlus(), 24);
        geneXPop.draw(imgDotsGeneX, 255);
        ImagePlus[] imgColors = {imgCells.getImagePlus(), imgDotsGeneX.getImagePlus(), null, imgVessel, imgCellNumbers.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(main.cal);
        IJ.run(imgObjects, "Enhance Contrast", "saturated=0.35");

        // Save images
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDirResults + rootName + "_DotsObjects.tif");
        imgCells.closeImagePlus();
        imgDotsGeneX.closeImagePlus();
    }
}
