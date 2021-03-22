/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Vessel_Scope_Utils;

/**
 *
 * @author phm
 */
public class Cell {
    // index
    private int index;
   
    // volume
    private double cellVol;
    private double zCell;
    
    // Gene X channel
   
    // cell integrated intensity in gene X channel
    private double cellGeneXInt;
    // cell mean background intensity in gene X channel
    private double cellGeneXBgInt;
    // dots volume
    private double geneXDotsVol;
    
    // integrated intensity of total gene X dots
    private double geneXDotsInt;
    
    //number of dots in cell based on cell intensity channel
    private int nbGeneXDotsCellInt;

    // number of dost in cell based on dots segmentation intensity
    private int nbGeneXDotsSegInt;
   
	
	public Cell(int index, double cellVol, double zCell,double cellGeneXInt, double cellGeneXBgInt, double geneXDotsVol, double geneXDotsInt, int nbGeneXDotsCellInt, int nbGeneXDotsSegInt) {
            this.index = index;
            this.cellVol = cellVol;
            this.zCell = zCell;
            this.cellGeneXInt = cellGeneXInt;
            this.cellGeneXBgInt = cellGeneXBgInt;
            this.geneXDotsVol = geneXDotsVol;
            this.geneXDotsInt = geneXDotsInt;
            this.nbGeneXDotsCellInt = nbGeneXDotsCellInt;
            this.nbGeneXDotsSegInt = nbGeneXDotsSegInt;
	}
        
        public void setIndex(int index) {
            this.index = index;
	}
        
        public void setCellVol(double cellVol) {
            this.cellVol = cellVol;
	}
        
        public void setzCell(double zCell) {
            this.zCell = zCell;
	}
        
        public void setCellGeneXInt(double cellGeneXInt) {
            this.cellGeneXInt = cellGeneXInt;
	}
        
        public void setCellGeneXBgInt(double cellGeneXBgInt) {
            this.cellGeneXBgInt = cellGeneXBgInt;
	}
        
        public void setGeneXDotsVol(double geneXDotsVol) {
            this.geneXDotsVol = geneXDotsVol;
        }
        
        public void setGeneXDotsInt(double geneXDotsInt) {
            this.geneXDotsInt = geneXDotsInt;
        }
        
        public void setnbGeneXDotsCellInt(int nbGeneXDotsCellInt) {
            this.nbGeneXDotsCellInt = nbGeneXDotsCellInt;
        }
        
        public void setnbGeneXDotsSegInt(int nbGeneXDotsSegInt) {
            this.nbGeneXDotsCellInt = nbGeneXDotsCellInt;
        }
        
        public int getIndex() {
            return index;
        }
        
        public double getCellVol() {
            return cellVol;
        }
        
        public double getzCell() {
            return zCell;
        }
        
        public double getCellGeneXInt() {
            return cellGeneXInt;
	}
        
        public double getCellGeneXBgInt() {
            return cellGeneXBgInt;
	}
        
        public double getGeneXDotsVol() {
            return geneXDotsVol;
        }
        
        public double getGeneXDotsInt() {
            return geneXDotsInt;
        }
        
        public int getnbGeneXDotsCellInt() {
            return nbGeneXDotsCellInt;
        }
        
        public int getnbGeneXDotsSegInt() {
            return nbGeneXDotsSegInt;
        }
}
