/*
    GNU General Public License
    CacheWolf is a software for PocketPC, Win and Linux that
    enables paperless caching.
    It supports the sites geocaching.com and opencaching.de

    Copyright (C) 2006  CacheWolf development team
	See http://www.cachewolf.de/ for more information.
    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; version 2 of the License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/
package CacheWolf.exp;

import CacheWolf.MainForm;
import CacheWolf.Preferences;
import CacheWolf.controls.InfoBox;
import CacheWolf.database.*;
import CacheWolf.navi.TransformCoordinates;
import CacheWolf.utils.Common;
import CacheWolf.utils.MyLocale;
import ewe.filechooser.FileChooser;
import ewe.filechooser.FileChooserBase;
import ewe.io.*;
import ewe.io.File;
import ewe.sys.Handle;
import ewe.ui.FormBase;
import ewe.ui.ProgressBarForm;
import ewe.util.StringTokenizer;

/**
 * @author Kalle
 * @author TweetyHH Class for Exporting direct to Explorists *.gs Files. Caches will be exported in files with maximum of 200 Caches.
 */

public class ExploristExporter {
    // starts with no ui for file selection
    final static int TMP_FILE = 0;
    // brings up a screen to select a file
    final static int ASK_FILE = 1;

    // selection, which method should be called
    final static int NO_PARAMS = 0;
    final static int LAT_LON = 1;
    final static int COUNT = 2;

    CacheDB cacheDB;
    // mask in file chooser
    String mask = "*.gs";
    // decimal separator for lat- and lon-String
    char decimalSeparator = '.';
    // if true, the complete cache details are read
    // before a call to the record method is made
    boolean needCacheDetails = true;

    // name of exporter for saving pathname
    String expName;

    public ExploristExporter() {
        cacheDB = MainForm.profile.cacheDB;
        expName = this.getClass().getName();
        // remove package
        expName = expName.substring(expName.indexOf(".") + 1);
    }

    public void doIt() {
        File configFile = new File("magellan.cfg");
        if (configFile.exists()) {
            FileChooser fc = new FileChooser(FileChooserBase.DIRECTORY_SELECT, Preferences.itself().getExportPath(expName + "Dir"));
            fc.setTitle(MyLocale.getMsg(2104, "Choose directory for exporting .gs files"));
            String targetDir;
            if (fc.execute() != FormBase.IDCANCEL) {
                targetDir = fc.getChosen() + "/";
                Preferences.itself().setExportPref(expName + "Dir", targetDir);

                CWPoint centre = MainForm.profile.center;
                try {
                    LineNumberReader reader = new LineNumberReader(new BufferedReader(new FileReader(configFile)));
                    String line, fileName, coordinate;
                    while ((line = reader.readLine()) != null) {
                        StringTokenizer tokenizer = new StringTokenizer(line, "=");
                        fileName = targetDir + tokenizer.nextToken().trim() + ".gs";
                        coordinate = tokenizer.nextToken().trim();
                        CWPoint point = new CWPoint(coordinate);
                        DistanceComparer dc = new DistanceComparer(point);
                        cacheDB.sort(dc, false);
                        doIt(fileName);
                    }
                    reader.close();
                } catch (FileNotFoundException e) {
                    new InfoBox(MyLocale.getMsg(2100, "Explorist Exporter"), MyLocale.getMsg(2101, "Failure at loading magellan.cfg\n" + e.getMessage())).wait(FormBase.OKB);
                } catch (IOException e) {
                    new InfoBox(MyLocale.getMsg(2100, "Explorist Exporter"), MyLocale.getMsg(2103, "Failure at reading magellan.cfg\n" + e.getMessage())).wait(FormBase.OKB);
                } finally {
                    cacheDB.sort(new DistanceComparer(centre), false);
                }
            }
        } else {
            doIt(null);
        }
    }

    /**
     * Does the most work for exporting data
     */
    public void doIt(String baseFileName) {
        File outFile;
        String fileBaseName;
        String str = null;
        CacheHolder ch;
        ProgressBarForm pbf = new ProgressBarForm();
        Handle h = new Handle();

        if (baseFileName == null) {
            outFile = getOutputFile();
            if (outFile == null)
                return;
        } else {
            outFile = new File(baseFileName);
        }

        fileBaseName = outFile.getFullPath();
        // cut .gs
        fileBaseName = fileBaseName.substring(0, fileBaseName.length() - 3);

        pbf.showMainTask = false;
        pbf.setTask(h, "Exporting ...");
        pbf.exec();

        int counter = cacheDB.countVisible();
        int expCount = 0;

        try {
            // Set initial value for outp to calm down compiler
            PrintWriter outp = new PrintWriter(new BufferedWriter(new FileWriter(new File(fileBaseName + expCount / 200 + ".gs"))));
            for (int i = 0; i < cacheDB.size(); i++) {
                ch = cacheDB.get(i);
                if (ch.isVisible()) {
                    // all 200 caches we need a new file
                    if (expCount % 200 == 0 && expCount > 0) {
                        outp.close();
                        outp = new PrintWriter(new BufferedWriter(new FileWriter(new File(fileBaseName + expCount / 200 + ".gs"))));
                    }

                    expCount++;
                    h.progress = (float) expCount / (float) counter;
                    h.changed();
                    str = record(ch);
                    if (str != null)
                        outp.print(str);
                }// if

            }// for
            str = trailer();

            if (str != null)
                outp.print(str);

            outp.close();
            pbf.exit(0);
        } catch (IOException ioE) {
            Preferences.itself().log("Error opening " + outFile.getName(), ioE);
        }
        // try
    }

    /**
     * uses a filechooser to get the name of the export file
     *
     * @return
     */
    public File getOutputFile() {
        File file;
        FileChooser fc = new FileChooser(FileChooserBase.SAVE, Preferences.itself().getExportPath(expName));
        fc.setTitle(MyLocale.getMsg(2102, "Select target file:"));
        fc.addMask(mask);
        if (fc.execute() != FormBase.IDCANCEL) {
            file = fc.getChosenFile();
            Preferences.itself().setExportPref(expName, file.getPath());
            return file;
        } else {
            return null;
        }
    }

    /**
     * this method can be overided by an exporter class
     *
     * @param ch cachedata
     * @return formated cache data
     */
    public String record(CacheHolder ch) {
        CacheHolderDetail det = ch.getDetails();
        /*
         * static protected final int GC_AW_PARKING = 50;
         * static protected final int GC_AW_STAGE_OF_MULTI = 51;
         * static protected final int GC_AW_QUESTION = 52;
         * static protected final int GC_AW_FINAL = 53;
         * static protected final int GC_AW_TRAILHEAD = 54;
         * static protected final int GC_AW_REFERENCE = 55;
         */
        StringBuffer sb = new StringBuffer();
        sb.append("$PMGNGEO,");
        sb.append(ch.getWpt().getLatDeg(TransformCoordinates.DMM));
        sb.append(ch.getWpt().getLatMin(TransformCoordinates.DMM));
        sb.append(",");
        sb.append("N,");
        sb.append(ch.getWpt().getLonDeg(TransformCoordinates.DMM));
        sb.append(ch.getWpt().getLonMin(TransformCoordinates.DMM));
        sb.append(",");
        sb.append("E,");
        sb.append("0000,"); // Height
        sb.append("M,"); // in meter
        sb.append(ch.getCode());
        sb.append(",");
        String add = "";
        if (ch.isAddiWpt()) {
            if (ch.getType() == 50) {
                add = "Pa:";
            } else if (ch.getType() == 51) {
                add = "St:";
            } else if (ch.getType() == 52) {
                add = "Qu:";
            } else if (ch.getType() == 53) {
                add = "Fi:";
            } else if (ch.getType() == 54) {
                add = "Tr:";
            } else if (ch.getType() == 55) {
                add = "Re:";
            }
            sb.append(add);
        }
        sb.append(ch.getCode() + " " + removeCommas(ch.getName()));
        sb.append(",");
        sb.append(removeCommas(ch.getOwner()));
        sb.append(",");
        sb.append(removeCommas(Common.rot13(det.getHints())));
        sb.append(",");

        if (!add.equals("")) { // Set Picture in Explorist to Virtual for Addis
            sb.append("Virtual Cache");
        } else {
            sb.append(CacheType.type2GSTypeTag(ch.getType()));
        }
        sb.append(",");
        sb.append(toGsDateFormat(ch.getHidden())); // created - DDMMYYY, YYY = year - 1900
        sb.append(",");
        String lastFound = "0000";
        for (int i = 0; i < det.getCacheLogs().size(); i++) {
            if (det.getCacheLogs().getLog(i).isFoundLog() && det.getCacheLogs().getLog(i).getDate().compareTo(lastFound) > 0) {
                lastFound = det.getCacheLogs().getLog(i).getDate();
            }
        }

        sb.append(toGsDateFormat(lastFound)); // lastFound - DDMMYYY, YYY = year - 1900
        sb.append(",");
        sb.append(CacheTerrDiff.longDT(ch.getDifficulty()));
        sb.append(",");
        sb.append(CacheTerrDiff.longDT(ch.getTerrain()));
        sb.append("*41");
        return Exporter.simplifyString(sb.toString() + "\r\n");
    }

    /**
     * this method can be overided by an exporter class
     *
     * @return formated trailer data
     */
    public String trailer() {
        return "$PMGNCMD,END*3D\n";
    }

    /**
     * Changes "," in "." in the input String
     *
     * @param input
     * @return changed String
     */
    private String removeCommas(String input) {
        return input.replace(',', '.');
    }

    /**
     * change the Dateformat from "yyyy-mm-dd" to ddmmyyy, where yyy is years after 1900
     *
     * @param input Date in yyyy-mm-dd
     * @return Date in ddmmyyy
     */
    private String toGsDateFormat(String input) {
        if (input.length() >= 10) {
            return input.substring(8, 10) + input.substring(5, 7) + "1" + input.substring(2, 4);
        } else {
            return "";
        }
    }

}
