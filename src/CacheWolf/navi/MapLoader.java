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
package CacheWolf.navi;

import CacheWolf.MainForm;
import CacheWolf.Preferences;
import CacheWolf.controls.InfoBox;
import CacheWolf.database.*;
import CacheWolf.utils.*;
import ewe.fx.Point;
import ewe.io.*;
import ewe.io.FileBase;
import ewe.sys.Time;
import ewe.ui.FormBase;
import ewe.util.*;

/**
 * To Download maps! Expedia removed (is dead since 2012) http://www.expedia.de/pub/agent.dll?qscr=mrdt&ID=3kQaz.&CenP=48.09901667,11.35688333&Lang=EUR0407&Alti=1&Size=600,600&Offs=0.000000,0.000000&Pins=|5748| oder
 * http://www.expedia.de/pub/agent.dll?qscr=mrdt&ID=3kQaz.&CenP=48.15,11.5833&Alti=2&Lang=EUR0407&Size=900,900&Offs=0,0&MapS=0&Pins=|48.15,11.5833|4|48.15,11.5833&Pins=|48.15,11.5833|1|48.15,%2011.5833||
 * <p>
 * start offset for language file: 4800
 */
public class MapLoader {
    private Vector onlineMapServices = new Vector();
    private OnlineMapService currentOnlineMapService;
    private CWPoint topleft;
    private CWPoint bottomright;
    private int numMapsX;
    private int numMapsY;
    private double latinc;
    private double loninc;
    private Point sizeInPixels;
    private float scale;
    private boolean fetchOnlyMapWithCache = false;
    private InfoBox progressInfobox;

    public MapLoader() {
        String wmspath = FileBase.getProgramDirectory() + "/" + "webmapservices";
        long start = new Time().getTime();
        InfoBox progressBox = null;
        boolean showprogress = false;

        onlineMapServices = new Vector();
        String dateien[];
        FileBugfix files = new FileBugfix(wmspath);
        String FileName;
        OnlineMapService tempOMS;
        dateien = files.list("*.wms", FileBase.LIST_FILES_ONLY);
        for (int i = 0; i < dateien.length; i++) {
            FileName = dateien[i];
            try {
                if (!showprogress && ((i & 0) == 0) && (new Time().getTime() - start > 100)) { // reason for (i & 7 == 0): test time only after i is incremented 15 times
                    showprogress = true;
                    progressBox = new InfoBox(MyLocale.getMsg(327, "Info"), MyLocale.getMsg(4800, "Loading online map services"));
                    progressBox.exec();
                    progressBox.waitUntilPainted(500);
                    ewe.sys.Vm.showWait(true);
                }
                tempOMS = new WebMapService(STRreplace.replace(wmspath + "/" + FileName, "//", "/"));
                onlineMapServices.add(tempOMS);
            } catch (Exception ex) {
                new InfoBox(MyLocale.getMsg(144, "Warning"), MyLocale.getMsg(4801, "Ignoring error while \n reading web map service definition file \n") + ex.toString()).exec();
            }
        }

        if (progressBox != null) {
            progressBox.close(0);
            ewe.sys.Vm.showWait(false);
        }
    }

    public void setFetchOnlyMapWithCache(boolean value) {
        fetchOnlyMapWithCache = value;
    }

    public String[] getAvailableOnlineMapServices(CWPoint center) {
        if (center.isValid()) {
            MapServiceComparer comparer = new MapServiceComparer(center);
            onlineMapServices.sort(comparer, false);
        }
        String[] s = new String[onlineMapServices.size()];
        for (int i = 0; i < onlineMapServices.size() - 1; i++) {
            BoundingBox bb = ((OnlineMapService) onlineMapServices.get(i)).boundingBox;
            String result = (bb.isInBound(center) ? "" : "---");
            result = result + ((OnlineMapService) onlineMapServices.get(i)).name;
            s[i] = result;
        }
        return s;
    }

    public void setCurrentMapService(int index) {
        if (index == -1)
            index = 0; // if no center set
        currentOnlineMapService = (OnlineMapService) onlineMapServices.get(index);
    }

    /**
     * @param center       centre of all tiles
     * @param radius       in meters
     * @param scale        3950 /2817.947378 = 1,046861280317350198581316446428 meters per pixel
     * @param sizeInPixels in pixels
     * @param overlapping  in pixels
     */
    public void setTiles(CWPoint center, float radius, float scale, Point sizeInPixels, int overlapping) {
        double metersPerLat = (1000 * (new CWPoint(0, 0)).getDistance(new CWPoint(1, 0)));
        double metersPerLon = metersPerLat * java.lang.Math.cos(center.latDec / 180 * java.lang.Math.PI);
        topleft = new CWPoint(center.latDec + (radius / metersPerLat), center.lonDec - (radius / metersPerLon));
        bottomright = new CWPoint(center.latDec - (radius / metersPerLat), center.lonDec + (radius / metersPerLon));

        this.setTiles(scale, sizeInPixels, overlapping);
    }

    public void setTiles(float scale, Point sizeInPixels, int overlapping) {
        double metersPerLat;
        double metersPerLon;
        if (topleft.equals(bottomright)) {
            // veraltet: Berechnung auf Kugel: double
            metersPerLat = (1000.0 * (new CWPoint(0, 0)).getDistance(new CWPoint(1, 0)));
            metersPerLon = metersPerLat * java.lang.Math.cos((topleft.latDec + bottomright.latDec) / 2 / 180 * java.lang.Math.PI);
        } else {
            CWPoint center = new CWPoint((topleft.latDec + bottomright.latDec) / 2.0, (topleft.lonDec + bottomright.lonDec) / 2.0);
            CWPoint centerleft = new CWPoint((topleft.latDec + bottomright.latDec) / 2.0, topleft.lonDec);
            CWPoint centerbottom = new CWPoint(bottomright.latDec, (topleft.lonDec + bottomright.lonDec) / 2.0);
            metersPerLat = 1000.0 * center.getDistance(centerbottom) / (center.latDec - centerbottom.latDec);
            metersPerLon = 1000.0 * center.getDistance(centerleft) / (center.lonDec - centerleft.lonDec);
        }

        double metersperpixel;
        WebMapService wms = (WebMapService) currentOnlineMapService;
        if (wms.requestUrlPart.equalsIgnoreCase("Tile")) {
            //8->406.67, 9->203.335, ..., 16->1.58, 17->0.79, 18->0.39
            metersperpixel = 37055.082;// 6-->1626.68;
            for (int i = 1; i < scale; i++) {
                metersperpixel = metersperpixel / 2.0;
            }
        } else {
            metersperpixel = wms.getMetersPerPixel(scale);
        }

        double pixelsPerLat = metersPerLat / metersperpixel;
        double pixelsPerLon = metersPerLon / metersperpixel;

        // over all pixelsize without borders
        double pixelsY = (topleft.latDec - bottomright.latDec) * pixelsPerLat;
        double pixelsX = -(topleft.lonDec - bottomright.lonDec) * pixelsPerLon;

        int borderX = overlapping;
        int borderY = overlapping;

        numMapsY = (int) java.lang.Math.ceil((pixelsY + borderY) / (sizeInPixels.y - borderY));
        numMapsX = (int) java.lang.Math.ceil((pixelsX + borderX) / (sizeInPixels.x - borderX));

        // increments calulated from pixel offset of tiles
        latinc = -(sizeInPixels.y - borderY) / pixelsPerLat;
        loninc = (sizeInPixels.x - borderX) / pixelsPerLon;

        // calculation of centre of first tile
        // additional sizeInPixels for borders and rounding
        double oversizeX = (numMapsX * (sizeInPixels.x - borderX) + borderX) - pixelsX;
        double oversizeY = (numMapsY * (sizeInPixels.y - borderY) + borderY) - pixelsY;
        // offset for upper left corner
        double offsetLat = -((sizeInPixels.y - oversizeY) / 2.0) / pixelsPerLat;
        double offsetLon = ((sizeInPixels.x - oversizeX) / 2.0) / pixelsPerLon;
        topleft.latDec = topleft.latDec + offsetLat;
        topleft.lonDec = topleft.lonDec + offsetLon;

        this.sizeInPixels = new Point();
        this.sizeInPixels.set(sizeInPixels);
        this.scale = scale;
    }

    public void downloadMaps(String mapsPath) {
        double lat = topleft.latDec;
        CWPoint center = new CWPoint();
        for (int row = 1; row <= numMapsY; row++) {
            double lon = topleft.lonDec;
            for (int col = 1; col <= numMapsX; col++) {
                center.set(lat, lon);
                if (!fetchOnlyMapWithCache || hasCache(center, latinc, loninc)) {
                    if (progressInfobox != null)
                        progressInfobox.setInfo(MyLocale.getMsg(4802, "Downloading calibrated (georeferenced) \n map image \n '") + currentOnlineMapService.getName() + MyLocale.getMsg(4803, "' \n Downloading tile \n row") + " " + row + " / "
                                + numMapsY + MyLocale.getMsg(4804, " column") + " " + col + " / " + numMapsX);
                    downloadMap(center, this.scale, this.sizeInPixels, mapsPath);
                    if (progressInfobox.isClosed())
                        return;
                }
                lon = lon + loninc;
            }
            lat = lat + latinc;
        }
    }

    private boolean hasCache(CWPoint center, double latinc, double loninc) {
        double lat = center.latDec - (latinc / 2.0);
        double lon = center.lonDec - (loninc / 2.0);
        CWPoint tl = new CWPoint(lat, lon);
        lat = center.latDec + (latinc / 2.0);
        lon = center.lonDec + (loninc / 2.0);
        CWPoint br = new CWPoint(lat, lon);
        BoundingBox maparea = new BoundingBox(tl, br);
        CacheDB cacheDB = MainForm.profile.cacheDB;
        for (int i = 0; i < cacheDB.size(); i++) {
            CacheHolder ch = cacheDB.get(i);
            if (maparea.isInBound(ch.getWpt())) {
                return true;
            }
        }
        return false;
    }

    public void setProgressInfoBox(InfoBox progrssInfoboxi) {
        progressInfobox = progrssInfoboxi;
    }

    /**
     * @param center
     * @param scale
     * @param sizeInPixels
     * @param path         including trailing "/"
     * @throws Exception
     */
    public void downloadMap(CWPoint center, float scale, Point sizeInPixels, String path) {
        WebMapService wms = (WebMapService) currentOnlineMapService;
        try {
            if (wms.requestUrlPart.startsWith("REQUEST")) {
                downloadMapFromWmsServer(center, scale, sizeInPixels, path);
            } else if (wms.requestUrlPart.equalsIgnoreCase("Tile")) {
                downloadMapFromTileServer(center, scale, path);
            } else if (wms.requestUrlPart.equalsIgnoreCase("Maperitive") || wms.requestUrlPart.equalsIgnoreCase("Kosmos")) {
                downloadMapExternal(center, scale, sizeInPixels, path);
            }
        } catch (Exception e) {
            this.progressInfobox.addWarning(MyLocale.getMsg(4805, "Tile") + " " + MyLocale.getMsg(4806, ": Ignoring error:") + " " + e.getMessage());
        }

    }

    private void downloadMapFromWmsServer(CWPoint center, float scale, Point sizeInPixels, String path) throws Exception {
        MapInfoObject mio = currentOnlineMapService.getMapInfoObject(center, scale, sizeInPixels);
        String filename = createFilename(mio.getCenter(), mio.scale);
        String imagename = filename; //mio.createMapName(filename);

        mio.getMapImageFileNameObject().setMapName(imagename);
        mio.getMapImageFileNameObject().setPath(MainForm.profile.getMapsSubDir(path));

        String imagetype = currentOnlineMapService.getImageFileExt();
        String url = currentOnlineMapService.getUrlForCenterScale(center, scale, sizeInPixels);
        String fName = path + imagename + imagetype;
        File fn = new File(path + imagename + ".wfl");
        File fn1 = new File(fName);
        if (!fn.exists() || fn.length() == 0 || !fn1.exists() || fn1.length() == 0) {
            Preferences.itself().log(url);
            downloadImage(url, fName);
            mio.saveWFL();
        }
    }

    private void downloadMapFromTileServer(CWPoint center, float scale, String path) throws Exception {
        // Point pixelsize is always 256x256
        WebMapService wms = (WebMapService) currentOnlineMapService;
        int x, y, zoom;
        double latRad = center.latDec * Math.PI / 180.0;
        zoom = (int) scale; // 8->406.67, 9->203.335, ..., 16->1.58, 17->0.79, 18->0.39
        int n = (int) Math.pow(2, zoom);
        x = (int) (n * ((center.lonDec + 180.0) / 360.0));
        y = (int) (n * (1 - (Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI)) / 2);
        String url = STRreplace.replace(wms.mainUrl, "{x}", "" + x);
        url = STRreplace.replace(url, "{y}", "" + y);
        url = STRreplace.replace(url, "{z}", "" + zoom);
        String targetPath = path + wms.filename + "/" + zoom + "/" + x;
        File f = new File(targetPath);
        if (f.mkdirs()) {
            String fName = targetPath + "/" + y + wms.imageFileExt;
            File fn = new File(fName);
            if (!fn.exists() || fn.length() == 0) {
                downloadImage(url, fName);
                // get scale
                // MapImageFileNameObject MapImageFileNameObject = new MapImageFileNameObject(targetPath + "/", "" + y, wms.imageFileExt);
                // MapInfoObject mio = new MapInfoObject(x, y, zoom, MapImageFileNameObject);
            }
        }
    }

    private void downloadMapExternal(CWPoint center, float scale, Point sizeInPixels, String path) throws Exception {
        WebMapService wms = (WebMapService) currentOnlineMapService;
        MapInfoObject mio = wms.getMapInfoObject(center, scale, sizeInPixels);
        String filename = createFilename(mio.getCenter(), mio.scale);
        String imagename = filename; // mio.createMapName(filename);

        mio.getMapImageFileNameObject().setMapName(imagename);
        mio.getMapImageFileNameObject().setPath(MainForm.profile.getMapsSubDir(path));
        String imagetype = wms.getImageFileExt();
        String fName = path + imagename + imagetype;
        BoundingBox maparea = wms.CenterScaleToArea(center, scale, sizeInPixels);
        CWPoint bottomleft = new CWPoint(maparea.bottomright.latDec, maparea.topleft.lonDec);
        CWPoint topright = new CWPoint(maparea.topleft.latDec, maparea.bottomright.lonDec);

        String mapProgramPath = wms.versionUrlPart + "/";
        mapProgramPath = mapProgramPath.replace('/', FileBase.separatorChar);
        String mapProgram = mapProgramPath + wms.mainUrl;
        File f = new File(mapProgram);
        if (!f.exists() || !f.canRead()) {
            new InfoBox(MyLocale.getMsg(5500, "Error"), MyLocale.getMsg(1834, "Please enter the correct path to Kosmos.Console.exe into the wms-file.")).wait(FormBase.OKB);
            return;
        }

        String mapProgramParams = "";

        if (wms.requestUrlPart.equalsIgnoreCase("Kosmos")) {
            // minx miny maxx maxy + sizeInPixels.x
            mapProgramParams = "bitmapgen" + " \"" + FileBase.getProgramDirectory().replace('/', File.separatorChar) + "\\" + wms.serviceTypeUrlPart + "\"" + " \"" + path.replace('/', File.separatorChar) + imagename + imagetype + "\"" + " -mb "
                    + bottomleft.toString(TransformCoordinates.LAT_LON).replace(',', ' ') + " " + topright.toString(TransformCoordinates.LAT_LON).replace(',', ' ') + " -w " + sizeInPixels.x;
            CWWrapper.execute(mapProgram + " " + mapProgramParams);
        } else {
            if (wms.requestUrlPart.equalsIgnoreCase("Maperitive")) {
                // Maperitive runs on Windows and Linux
                // generating scriptfile for Maperitive from wmsfile
                String cwPath = FileBase.getProgramDirectory().replace('/', FileBase.separatorChar) + FileBase.separatorChar;
                String scriptFileName = cwPath + "maperitive.script";

                PrintWriter outp = new PrintWriter(new BufferedWriter(new FileWriter(scriptFileName)));
                outp.println("use-ruleset alias=default");
                outp.println("clear-map");

                if (wms.serviceTypeUrlPart.equals("")) {
                    outp.println("add-web-map");
                } else {
                    String s = STRreplace.replace(wms.serviceTypeUrlPart, ",", " ");
                    String[] t = mString.split(s, ' ');
                    for (int i = 0; i < t.length; i++) {
                        if (t[i].length() > 0) {
                            outp.println("add-web-map provider=" + t[i]);
                        }
                    }
                }

                if (!wms.stylesUrlPart.equals("")) {
                    String myrules = mapProgramPath + wms.stylesUrlPart.replace('/', FileBase.separatorChar);
                    outp.println("use-ruleset location=" + myrules);
                    // outp.println("apply-ruleset");
                }
                if (!wms.layersUrlPart.equals("")) {
                    outp.println("clear-map");
                    outp.println("load-source " + mapProgramPath + wms.layersUrlPart.replace('/', FileBase.separatorChar));
                    // implicit does apply-ruleset
                }

                String koords = bottomleft.toString(TransformCoordinates.LON_LAT) + "," + topright.toString(TransformCoordinates.LON_LAT);
                outp.println("set-geo-bounds " + koords);
                outp.println("zoom-bounds");
                if (path.indexOf(':') == 1) {
                    outp.print("export-bitmap file=" + "\"" + fName + "\"");
                } else {
                    outp.print("export-bitmap file=" + fName);
                }
                String pxSize = " width=" + sizeInPixels.x + " height=" + sizeInPixels.y;
                outp.print(pxSize);
                outp.println(" ozi=true");
                outp.println("exit");
                outp.close();
                // executing the generated script
                if (mapProgram.indexOf(':') == 1) {
                    // Windows
                    // mapProgramParams = "-exitafter " + "\"" + scriptFileName + "\"";
                    mapProgramParams = "\"" + scriptFileName + "\"";
                } else {
                    // Linux
                    // mapProgramParams = "-exitafter " + scriptFileName;
                    mapProgramParams = scriptFileName;
                }
                if (CWWrapper.execute(mapProgram + " " + mapProgramParams)) {
                    // preparation for generating wfl from the ozi map-file
                    Vector GCPs = map2wfl(path + imagename);
                    mio.evalGCP(GCPs, sizeInPixels.x, sizeInPixels.y);
                    // can not supress generation of pgw,jgw-file
                    File georefFile = new File(fName + ".georef");
                    georefFile.delete();
                } else {
                    return;
                }
            }
        }
        mio.saveWFL();
    }

    private Vector map2wfl(String pathAndImageName) {
        Vector GCPs = new Vector();
        File mapFile = new File(pathAndImageName + ".map");
        if (mapFile.exists()) {
            GCPoint gcp1 = new GCPoint();
            GCPoint gcp2 = new GCPoint();
            GCPoint gcp3 = new GCPoint();
            GCPoint gcp4 = new GCPoint();
            GCPoint gcpG = new GCPoint();
            String line = "";
            String[] parts;
            try {
                FileReader inMap = new FileReader(pathAndImageName + ".map");
                while ((line = inMap.readLine()) != null) {
                    if (line.equals("MMPNUM,4")) {

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        gcp1.bitMapX = Common.parseInt(parts[2]);
                        gcp1.bitMapY = Common.parseInt(parts[3]);
                        if (gcp1.bitMapX == 0)
                            gcp1.bitMapX = 1;
                        if (gcp1.bitMapY == 0)
                            gcp1.bitMapY = 1;

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        gcp2.bitMapX = Common.parseInt(parts[2]);
                        gcp2.bitMapY = Common.parseInt(parts[3]);
                        if (gcp2.bitMapX == 0)
                            gcp2.bitMapX = 1;
                        if (gcp2.bitMapY == 0)
                            gcp2.bitMapY = 1;

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        gcp3.bitMapX = Common.parseInt(parts[2]);
                        gcp3.bitMapY = Common.parseInt(parts[3]);
                        if (gcp3.bitMapX == 0)
                            gcp3.bitMapX = 1;
                        if (gcp3.bitMapY == 0)
                            gcp3.bitMapY = 1;
                        // imageWidth = gcp3.bitMapX;
                        // imageHeight = gcp3.bitMapY;

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        gcp4.bitMapX = Common.parseInt(parts[2]);
                        gcp4.bitMapY = Common.parseInt(parts[3]);
                        if (gcp4.bitMapX == 0)
                            gcp4.bitMapX = 1;
                        if (gcp4.bitMapY == 0)
                            gcp4.bitMapY = 1;

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        if (Common.getDigSeparator() == ',') {
                            parts[3] = parts[3].replace('.', ',');
                            parts[2] = parts[2].replace('.', ',');
                        }
                        gcpG = new GCPoint(Common.parseDouble(parts[3]), Common.parseDouble(parts[2]));
                        gcpG.bitMapX = gcp1.bitMapX;
                        gcpG.bitMapY = gcp1.bitMapY;
                        GCPs.add(gcpG);

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        if (Common.getDigSeparator() == ',') {
                            parts[3] = parts[3].replace('.', ',');
                            parts[2] = parts[2].replace('.', ',');
                        }
                        gcpG = new GCPoint(Common.parseDouble(parts[3]), Common.parseDouble(parts[2]));
                        gcpG.bitMapX = gcp2.bitMapX;
                        gcpG.bitMapY = gcp2.bitMapY;
                        GCPs.add(gcpG);

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        if (Common.getDigSeparator() == ',') {
                            parts[3] = parts[3].replace('.', ',');
                            parts[2] = parts[2].replace('.', ',');
                        }
                        gcpG = new GCPoint(Common.parseDouble(parts[3]), Common.parseDouble(parts[2]));
                        gcpG.bitMapX = gcp3.bitMapX;
                        gcpG.bitMapY = gcp3.bitMapY;
                        GCPs.add(gcpG);

                        line = inMap.readLine();
                        parts = mString.split(line, ',');
                        if (Common.getDigSeparator() == ',') {
                            parts[3] = parts[3].replace('.', ',');
                            parts[2] = parts[2].replace('.', ',');
                        }
                        gcpG = new GCPoint(Common.parseDouble(parts[3]), Common.parseDouble(parts[2]));
                        gcpG.bitMapX = gcp4.bitMapX;
                        gcpG.bitMapY = gcp4.bitMapY;
                        GCPs.add(gcpG);
                    } // if
                } // while
                inMap.close();
            } catch (IllegalArgumentException ex) { // is thrown from Convert.toDouble and saveWFL if affine[0-5]==0 NumberFormatException is a subclass of IllegalArgumentExepction
                Preferences.itself().log(MyLocale.getMsg(4117, "Error while importing .map-file: "), ex);
            } catch (IOException ex) {
                Preferences.itself().log(MyLocale.getMsg(4118, "IO-Error while reading or writing calibration file"), ex);
            }
            mapFile.delete();
        } else { // if map file.exists
            Preferences.itself().log(MyLocale.getMsg(4119, "No calibration file found for: "), null);
        }
        return GCPs;
    }

    public String createFilename(CWPoint center, float scale) {
        String filename = Common.ClearForFileName(currentOnlineMapService.getNameForFileSystem() + "_s" + Common.DoubleToString(scale, 0, 1) + "_c" + center.toString(TransformCoordinates.LAT_LON).replace(',', '-'));
        return filename;
    }

    /**
     * @param url
     * @param datei path and name of file to save to
     */
    public void downloadImage(String url, String datei) throws IOException {
        File dateiF = new File(datei);
        if (!dateiF.exists()) {
            UrlFetcher.fetchDataFile(url, datei);
            String ct = null;
            // todo perhaps compare ct with wms Definition , set File Extension accordingly
            try {
                ct = (String) UrlFetcher.getDocumentProperties().getValue("content-type", "no-content-type provided");
            } catch (Exception e) {
                ct = "document property content-type does not exist!";
            }
            if (!ct.substring(0, 5).equalsIgnoreCase("image")) {
                dateiF = new File(datei);
                dateiF.delete();
                throw new IOException(MyLocale.getMsg(4808, "downloadImage: content-type:") + " " + ct + MyLocale.getMsg(4809, " is not an image, begin of content:") + " (deleted)");
            }
        }
    }

    /**
     * @return the currentOnlineMapService
     */
    public OnlineMapService getCurrentOnlineMapService() {
        return currentOnlineMapService;
    }

    /**
     * @param currentOnlineMapService the currentOnlineMapService to set
     */
    public void setCurrentOnlineMapService(OnlineMapService currentOnlineMapService) {
        this.currentOnlineMapService = currentOnlineMapService;
    }

    /**
     * @return the numMapsY
     */
    public int getNumMapsY() {
        return numMapsY;
    }

    /**
     * @return the onlineMapServices
     */
    public Vector getOnlineMapServices() {
        return onlineMapServices;
    }

    /**
     * @return the topleft
     */
    public CWPoint getTopleft() {
        return topleft;
    }

    /**
     * @param topleft the topleft to set
     */
    public void setTopleft(CWPoint topleft) {
        this.topleft = topleft;
    }

    /**
     * @return the bottomright
     */
    public CWPoint getBottomright() {
        return bottomright;
    }

    /**
     * @param bottomright the bottomright to set
     */
    public void setBottomright(CWPoint bottomright) {
        this.bottomright = bottomright;
    }
}

class OnlineMapService {
    /**
     * Esentially the same as name, but used for the file system. It will be part of the names of the downloaded images
     */
    protected String filename;
    /**
     * Friendly name of the service
     */
    String name;
    /**
     * Type of map (used as directory name when downloading maps. We currently have "topo" and "photo" defined as map types
     */
    String mapType;
    String mainUrl, takenFromUrl, getCapabilitiesUrl, specialUrl;
    /**
     * including "."
     */
    String imageFileExt; // ".gif", ".jpg"...
    double recommendedScales[];
    int preselectedRecScaleIndex = 0;
    double minscale;
    double maxscale;
    BoundingBox boundingBox;
    int minPixelSize;
    int maxPixelSize;
    String prefWidthPixelSize;
    String prefHeightPixelSize;

    /**
     * This method is used in case the online map service provides only certain steps of zoomlevels. In this case the scale in meters per pixel must be returned, which will be used instead of the wished scale.
     *
     * @param scale
     * @return
     */
    public float getMetersPerPixel(float scale) {
        return scale;
    }

    public String getImageFileExt() {
        return imageFileExt;
    }

    /**
     * Overload this to integrate name of layers
     *
     * @return friendly service name
     */
    public String getName() {
        return name;
    }

    /**
     * This method is called to get a name of the online map service which will be part of the filename used for the downloaded image
     *
     * @return friendly service name
     */
    public String getNameForFileSystem() {
        return filename;
    }

    public String getMapType() {
        return mapType;
    }

    /**
     * Overload this and return the URL to the map image, don't call super. Alternatively overload getUrlForBoundingBoxInternal You must overload either this method or getUrlForBoundingBox
     *
     * @param center
     * @param scale
     * @param sizeInPixels
     * @return
     */
    public String getUrlForCenterScale(CWPoint center, float scale, Point sizeInPixels) {
        BoundingBox bbox = CenterScaleToArea(center, scale, sizeInPixels);
        String url = getUrlForBoundingBoxInternal(bbox, sizeInPixels, scale);
        return url;
    }

    /**
     * This is made protected and named "...Internal" because a lot of services don't work correctly when a map is requested, that is not exactly quadratic --> alway use getUrlForCenter...
     *
     * @param surArea
     * @param sizeInPixels
     * @return
     */
    protected String getUrlForBoundingBoxInternal(BoundingBox surArea, Point sizeInPixels, double scaleInput) {
        return null;
    }

    /**
     * overload this if your map service uses a special projection an return an Area that is quadratic in that projection
     *
     * @param center
     * @param scale
     * @param pixelsize
     * @return
     */
    public BoundingBox CenterScaleToArea(CWPoint center, float scale, Point sizeInPixels) {
        BoundingBox bbox = new BoundingBox();
        double halfdiagonal = Math.sqrt(sizeInPixels.x * sizeInPixels.x + sizeInPixels.y * sizeInPixels.y) / 2 * scale / 1000;
        bbox.topleft = center.project(-45, halfdiagonal);
        bbox.bottomright = center.project(135, halfdiagonal);
        return bbox;
    }

    protected MapInfoObject getMapInfoObjectInternal(BoundingBox maparea, Point pixelsize) {
        throw new IllegalArgumentException(MyLocale.getMsg(4811, "OnlineMapService: getMapInfoObjectInternal(BoundingBox maparea, Point pixelsize):\n This method must be overloaded in order to be able to use it"));
    }

    /**
     * Overload this (don't call super()) or alternatively overload getMapInfoObjectInternal
     *
     * @param center
     * @param scale
     * @param pixelsize
     * @return
     */
    public MapInfoObject getMapInfoObject(CWPoint center, float scale, Point pixelsize) {
        return getMapInfoObjectInternal(CenterScaleToArea(center, scale, pixelsize), pixelsize);
    }

    public String toString() {
        return getName();
    }
}

class WebMapService extends OnlineMapService {
    private static final int TOPLEFT_INDEX = 0;
    private static final int BOTTOMRIGHT_INDEX = 1;
    private static final int TOPRIGHT_INDEX = 2;
    private static final int BOTTOMLEFT_INDEX = 3;
    public String requestUrlPart;
    String layersUrlPart; //
    String versionUrlPart; // VERSION=1.1.0
    String serviceTypeUrlPart; // "SERVICE=WMS"
    int coordinateReferenceSystem[]; // WGS84: 4326, German GK: 31466 /
    String coordinateReferenceSystemUrlPart[]; // &SRS=EPSG:31466
    String imageFormatUrlPart; // FORMAT=image/png
    String stylesUrlPart; // STYLES=
    double minscaleWMS;
    double maxscaleWMS;
    Properties wms;

    /**
     * @param filename without file extension
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public WebMapService(String wmsFilePaN) throws IOException, IllegalArgumentException {
        // read wms-file
        FileInputStream in = new FileInputStream(wmsFilePaN);
        wms = new Properties();
        try {
            wms.load(in);
        }
        catch (Exception e) {
            //Intentionally empty
        }
        in.close();

        this.filename = Common.getPathAndFilename(FileBase.getFileExt(wmsFilePaN)); //only the name

        name = wms.getProperty("Name", "").trim();
        if (name.length() == 0){
            throw new IllegalArgumentException(MyLocale.getMsg(4812, "WebMapService: property >Name:< missing in file:\n") + filename);
	}

        mainUrl = wms.getProperty("MainUrl", "").trim();
        if (mainUrl.length() == 0){
            throw new IllegalArgumentException(MyLocale.getMsg(4813, "WebMapService: property >MainUrl:< missing in file:\n") + filename);
	}
        //takenFromUrl = wms.getProperty("TakenFromUrl", "").trim();
        //getCapabilitiesUrl = wms.getProperty("GetCapabilitiesUrl", "").trim();
        specialUrl = wms.getProperty("SpecialUrl", "").trim();
        mapType = wms.getProperty("MapType", "maptype_unknown").trim();
        serviceTypeUrlPart = wms.getProperty("ServiceTypeUrlPart", "SERVICE=WMS").trim();
        layersUrlPart = wms.getProperty("LayersUrlPart", "").trim();
        versionUrlPart = wms.getProperty("VersionUrlPart", "").trim();

        String tmp = wms.getProperty("CoordinateReferenceSystemCacheWolf", "").trim();
        if (tmp.equals("")){
            throw new IllegalArgumentException(MyLocale.getMsg(4814, "WebMapService: no CoordinateReferenceSystemCacheWolf given"));
        }
        String[] tmp2 = mString.split(tmp, ' ');
        coordinateReferenceSystem = new int[tmp2.length];
        for (int i = 0; i < tmp2.length; i++) {
            coordinateReferenceSystem[i] = Common.parseInt(tmp2[i].trim());
            if (!TransformCoordinates.isSupported(coordinateReferenceSystem[i])){
                throw new IllegalArgumentException(MyLocale.getMsg(4815, "Coordinate reference system not supported by CacheWolf:\n") + coordinateReferenceSystem[i]);
            }
        }
        tmp = STRreplace.replace(wms.getProperty("CoordinateReferenceSystemUrlPart", "").trim(), ":", "%3A");
        if (tmp.length() == 0){
            throw new IllegalArgumentException(MyLocale.getMsg(4816, "WebMapService: property >CoordinateReferenceSystemUrlPart:< missing in file:\n") + filename);
        }
        tmp2 = mString.split(tmp, ' ');
        if (tmp2.length != coordinateReferenceSystem.length){
            throw new IllegalArgumentException(MyLocale.getMsg(4817, "number of strings in CoordinateReferenceSystemUrlPart (") + 
                                               tmp2.length + 
                                               MyLocale.getMsg(4818, ") must match the number of codes in CoordinateReferenceSystemCacheWolf (") +
                                               coordinateReferenceSystem.length + 
                                               MyLocale.getMsg(4819, ") use normal space as separator"));}
        coordinateReferenceSystemUrlPart = new String[tmp2.length];
        for (int i = 0; i < tmp2.length; i++) {
            coordinateReferenceSystemUrlPart[i] = tmp2[i].trim();
            if (coordinateReferenceSystemUrlPart[i].length() == 0)
                throw new IllegalArgumentException(MyLocale.getMsg(4820, "WebMapService: property >CoordinateReferenceSystemUrlPart:< incorrect in file:\n") + filename);
        }
        requestUrlPart = wms.getProperty("RequestUrlPart", "REQUEST=GetMap").trim();
        imageFormatUrlPart = STRreplace.replace(wms.getProperty("ImageFormatUrlPart", "").trim(), "/", "%2F");
        stylesUrlPart = wms.getProperty("StylesUrlPart", "").trim();
        String topleftS = wms.getProperty("BoundingBoxTopLeftWGS84", "").trim();
        String bottomrightS = wms.getProperty("BoundingBoxBottomRightWGS84");
        bottomrightS.trim();
        CWPoint topleft = new CWPoint(topleftS);
        CWPoint bottomright = new CWPoint(bottomrightS);
        if (!topleft.isValid()){
            topleft.set(90, -180);
        }
        if (!bottomright.isValid()){
            bottomright.set(-90, 180);
        }
        boundingBox = new BoundingBox(topleft, bottomright);

        minscaleWMS = Common.parseDouble(wms.getProperty("MinScale", "0").trim());
        maxscaleWMS = Common.parseDouble(wms.getProperty("MaxScale", Common.DoubleToString(java.lang.Double.MAX_VALUE, 0, 0)));
        minscale = minscaleWMS / Math.sqrt(2); // in WMS scale is measured diagonal while in CacheWolf it is measured vertical
        maxscale = maxscaleWMS / Math.sqrt(2);
        imageFileExt = wms.getProperty("ImageFileExtension", "").trim();
        if (imageFileExt.length() == 0){
            throw new IllegalArgumentException(MyLocale.getMsg(4821, "WebMapService: property >ImageFileExtension:< missing in file:\n") + filename);
	}
        String[] recommendedScalesStr = mString.split(wms.getProperty("RecommendedScale", "5").trim(), ' ');
        
	// convert recommended scales to sorted doubel[], preselected is the first read
        if (recommendedScalesStr.length > 0) {
            double preselected = Common.parseDouble(recommendedScalesStr[0]);
            ewe.sys.Double[] recommendedScalesObj = new ewe.sys.Double[recommendedScalesStr.length];
            for (int i = 0; i < recommendedScalesObj.length; i++) {
                recommendedScalesObj[i] = new ewe.sys.Double();
                recommendedScalesObj[i].set(Common.parseDouble(recommendedScalesStr[i]));
            }
            Utils.sort(recommendedScalesObj, new StandardComparer(), false);

            recommendedScales = new double[recommendedScalesStr.length];
            for (int i = 0; i < recommendedScales.length; i++) {
                recommendedScales[i] = recommendedScalesObj[i].value;
                if (recommendedScales[i] == preselected)
                    preselectedRecScaleIndex = i;
            }
        }

        // some WMS limit the size of images they deliver - a swedish WMS delivers just 256x256 even if 500x500 or 1000x1000 was requested.
        // TLS - Server always have 256x256
        maxPixelSize = Integer.parseInt(wms.getProperty("MaxPixelSize", "-1").trim());
        if (maxPixelSize == -1)
            maxPixelSize = Integer.MAX_VALUE;

        minPixelSize = Integer.parseInt(wms.getProperty("MinPixelSize", "100").trim());
        if (minPixelSize == -1)
            minPixelSize = 0;

        prefWidthPixelSize = wms.getProperty("RecommendedWidthPixelSize", "1024").trim();
        prefHeightPixelSize = wms.getProperty("RecommendedHeightPixelSize", "1024").trim();
    }

    /**
     * @param maparea
     * @return [0] = topleft, [1] = bottomright, [2] = topright, [3] = bottomleft
     */
    private ProjectedPoint[] getGkArea(BoundingBox maparea) {
        ProjectedPoint[] ret = new ProjectedPoint[4];
        int crs = getCrs(maparea.getCenter());
        ret[TOPLEFT_INDEX] = TransformCoordinates.wgs84ToEpsg(maparea.topleft, coordinateReferenceSystem[crs]);
        ret[BOTTOMRIGHT_INDEX] = TransformCoordinates.wgs84ToEpsg(maparea.bottomright, coordinateReferenceSystem[crs]);
        ret[TOPRIGHT_INDEX] = ret[BOTTOMRIGHT_INDEX].cloneIt();
        ret[TOPRIGHT_INDEX].shift(ret[TOPLEFT_INDEX].getNorthing() - ret[BOTTOMRIGHT_INDEX].getNorthing(), 0);
        ret[BOTTOMLEFT_INDEX] = ret[BOTTOMRIGHT_INDEX].cloneIt();
        ret[BOTTOMLEFT_INDEX].shift(ret[TOPLEFT_INDEX].getEasting() - ret[BOTTOMRIGHT_INDEX].getEasting(), 1);

        return ret;
    }

    public BoundingBox CenterScaleToArea(CWPoint center, float scale, Point pixelsize) {
        BoundingBox bbox = new BoundingBox();
        int region = TransformCoordinates.getLocalProjectionSystem(coordinateReferenceSystem[0]);
        if (region > 0) {
            int epsg = coordinateReferenceSystem[getCrs(center)];
            ProjectedPoint cgk = TransformCoordinates.wgs84ToEpsg(center, epsg);
            ProjectedPoint tlgk = cgk.cloneIt();
            tlgk.shift(-pixelsize.x * scale / 2, 1);
            tlgk.shift(pixelsize.y * scale / 2, 0);
            ProjectedPoint brgk = cgk.cloneIt();
            brgk.shift(pixelsize.x * scale / 2, 1);
            brgk.shift(-pixelsize.y * scale / 2, 0);
            bbox.topleft = TransformCoordinates.ProjectedEpsgToWgs84(tlgk, epsg);
            bbox.bottomright = TransformCoordinates.ProjectedEpsgToWgs84(brgk, epsg);
        }
	else {
            switch (coordinateReferenceSystem[0]) {
                case TransformCoordinates.EPSG_WGS84:
                    bbox.topleft.set(center);
                    bbox.topleft.shift(-pixelsize.x * scale / 2, 1);
                    bbox.topleft.shift(pixelsize.y * scale / 2, 0);
                    bbox.bottomright.set(center);
                    bbox.bottomright.shift(pixelsize.x * scale / 2, 1);
                    bbox.bottomright.shift(-pixelsize.y * scale / 2, 0);
                    break;
                default:
                    throw new IllegalArgumentException("CenterScaleToArea: epsg: " + coordinateReferenceSystem[0] + " not supported");
            }
        }
        return bbox;
    }

    protected String getUrlForBoundingBoxInternal(BoundingBox maparea, Point sizeInPixels, double scaleInput) {
        if (!boundingBox.isOverlapping(maparea))
            throw new IllegalArgumentException(MyLocale.getMsg(4822, "area:") + " " + maparea.toString() + MyLocale.getMsg(4823, " not covered by service:") + " " + name + MyLocale.getMsg(4824, ", service area:") + " " + boundingBox.toString());
        // http://www.geoserver.nrw.de/GeoOgcWms1.3/servlet/TK25?SERVICE=WMS&VERSION=1.1.0&REQUEST=GetMap&SRS=EPSG:31466&BBOX=2577567.0149,5607721.7566,2578567.0077,5608721.7602&WIDTH=500&HEIGHT=500&LAYERS=Raster:TK25_KMF:Farbkombination&STYLES=&FORMAT=image/png
        CWPoint bottomleft = new CWPoint(maparea.bottomright.latDec, maparea.topleft.lonDec);
        CWPoint topright = new CWPoint(maparea.topleft.latDec, maparea.bottomright.lonDec);
        int crs = 0;
        String bbox = "BBOX=";
        int localsystem = TransformCoordinates.getLocalProjectionSystem(coordinateReferenceSystem[0]);
        if (localsystem > 0) {
            crs = getCrs(maparea.getCenter());
            ProjectedPoint[] gk = getGkArea(maparea);
            bottomleft = TransformCoordinates.ProjectedEpsgToWgs84(gk[BOTTOMLEFT_INDEX], coordinateReferenceSystem[crs]);
            topright = TransformCoordinates.ProjectedEpsgToWgs84(gk[TOPRIGHT_INDEX], coordinateReferenceSystem[crs]);
            bbox += TransformCoordinates.wgs84ToEpsg(bottomleft, coordinateReferenceSystem[crs]).toString(2, "", ",");
            bbox += "," + TransformCoordinates.wgs84ToEpsg(topright, coordinateReferenceSystem[crs]).toString(2, "", ",");
        } else if (coordinateReferenceSystem[0] == TransformCoordinates.EPSG_WGS84)
            bbox += bottomleft.toString(TransformCoordinates.LON_LAT) + "," + topright.toString(TransformCoordinates.LON_LAT);
        else
            throw new IllegalArgumentException(MyLocale.getMsg(4828, "Coordinate system not supported by cachewolf:") + " " + coordinateReferenceSystem.toString());
        String ret = mainUrl //
                + layersUrlPart //
                + "&" + imageFormatUrlPart//
                + "&" + serviceTypeUrlPart//
                + "&" + versionUrlPart //
                + "&" + requestUrlPart //
                + "&" + stylesUrlPart //
                + "&" + coordinateReferenceSystemUrlPart[crs] //
                + "&" + bbox //
                + "&" + "WIDTH=" + sizeInPixels.x //
                + "&" + "HEIGHT=" + sizeInPixels.y //
                ;
        return ret;
    }

    /**
     * This method gives the number in the array of coordinateReferenceSystems, which should be used a) if only one is in the array 0 is returned b) if there are more, find out which one matches the correct zone (e.g. Gauß-Krüger stripe)
     * <p>
     * Call this routine with center of the area (use BoundingBox.getcenter())
     *
     * @param p Point for which the epsg code is searched for
     * @return
     */
    private int getCrs(CoordinatePoint p) {
        int crsindex = 0;
        if (coordinateReferenceSystem.length > 1) {
            ProjectedPoint gkbl = TransformCoordinates.wgs84ToLocalsystem(p, TransformCoordinates.getLocalProjectionSystem(coordinateReferenceSystem[0]));
            // TODO: think / read about what to do if bottom left and top right are not in the same Gauss-Krüger stripe?
            int wantepsg = gkbl.getEpsgCode();
            for (crsindex = 0; crsindex < coordinateReferenceSystem.length; crsindex++) {
                if (coordinateReferenceSystem[crsindex] == wantepsg)
                    break;
            }
            if (crsindex >= coordinateReferenceSystem.length) {
                // not match
                for (crsindex = 0; crsindex < coordinateReferenceSystem.length; crsindex++) {
                    if (Math.abs(coordinateReferenceSystem[crsindex] - wantepsg) == 1)
                        break; // accept 1 zone deviation
                }
                if (crsindex >= coordinateReferenceSystem.length)
                    crsindex = -1;

            }
            if (crsindex < 0) {
                Preferences.itself().log(MyLocale.getMsg(4829, "getUrlForBoundingBox: Point:") + " " + gkbl.toString() + MyLocale.getMsg(4830, "no matching Gauß-Krüger-Stripe in the EPSG-code list in the .wms"));
                throw new IllegalArgumentException(MyLocale.getMsg(4829, "getUrlForBoundingBox: Point:") + " " + gkbl.toString() + MyLocale.getMsg(4830, "no matching Gauß-Krüger-Stripe in the EPSG-code list in the .wms"));
            }
        }
        return crsindex;
    }

    protected MapInfoObject getMapInfoObjectInternal(BoundingBox maparea, Point pixelsize) {
        if (!boundingBox.isOverlapping(maparea))
            throw new IllegalArgumentException(MyLocale.getMsg(4822, "area:") + " " + maparea.toString() + MyLocale.getMsg(4823, " not covered by service:") + " " + name + MyLocale.getMsg(4824, ", service area:") + " " + boundingBox.toString());
        Vector georef = new Vector(4);

        // calculate a rectangle in the according coordinate reference system
        CWPoint bottomleft = new CWPoint(maparea.bottomright.latDec, maparea.topleft.lonDec);
        CWPoint topright = new CWPoint(maparea.topleft.latDec, maparea.bottomright.lonDec);
        CWPoint topleft = new CWPoint(maparea.topleft);
        CWPoint bottomright = new CWPoint(maparea.bottomright);
        double metersperpixalhorizontal = (bottomright.getDistance(bottomleft) + topleft.getDistance(topright)) / 2 * 1000 / pixelsize.x;
        double metersperpixalvertical = (bottomright.getDistance(topright) + topleft.getDistance(bottomleft)) / 2 * 1000 / pixelsize.y;
        int region = TransformCoordinates.getLocalProjectionSystem(coordinateReferenceSystem[0]);
        if (region > 0) {
            ProjectedPoint[] gk = getGkArea(maparea);
            // bounding box in WMS is defined around the pixels, not exactly on the pixels --> the bounding box must be reduced on all edges by half a pixel
            gk[TOPLEFT_INDEX].shift(metersperpixalhorizontal / 2, 1);
            gk[TOPLEFT_INDEX].shift(-metersperpixalvertical / 2, 0);
            gk[BOTTOMRIGHT_INDEX].shift(-metersperpixalhorizontal / 2, 1);
            gk[BOTTOMRIGHT_INDEX].shift(metersperpixalvertical / 2, 0);
            gk[TOPRIGHT_INDEX].shift(-metersperpixalhorizontal / 2, 1);
            gk[TOPRIGHT_INDEX].shift(-metersperpixalvertical / 2, 0);
            gk[BOTTOMLEFT_INDEX].shift(metersperpixalhorizontal / 2, 1);
            gk[BOTTOMLEFT_INDEX].shift(metersperpixalvertical / 2, 0);

            topleft.set(gk[TOPLEFT_INDEX].getNorthing(), gk[TOPLEFT_INDEX].getEasting());
            bottomright.set(gk[BOTTOMRIGHT_INDEX].getNorthing(), gk[BOTTOMRIGHT_INDEX].getEasting());
            topright.set(gk[TOPRIGHT_INDEX].getNorthing(), gk[TOPRIGHT_INDEX].getEasting());
            bottomleft.set(gk[BOTTOMLEFT_INDEX].getNorthing(), gk[BOTTOMLEFT_INDEX].getEasting());
        } else if (coordinateReferenceSystem[0] == TransformCoordinates.EPSG_WGS84) {
            // bounding box in WMS is defined around the pixels, not exactly on the pixels --> the bounding box must be reduced on all edges by half a pixel
            topleft.shift(metersperpixalhorizontal / 2, 1);
            topleft.shift(-metersperpixalvertical / 2, 0);
            bottomright.shift(-metersperpixalhorizontal, 1);
            bottomright.shift(metersperpixalhorizontal, 0);
            topright = new CWPoint(topleft.latDec, bottomright.lonDec);
            bottomleft = new CWPoint(bottomright.latDec, topleft.lonDec);
        } else
            throw new IllegalArgumentException(MyLocale.getMsg(4831, "getMapInfoObject: Coordinate system not supported by cachewolf:") + " " + coordinateReferenceSystem);
        georef.add(new GCPoint(topleft, new Point(0, 0)));
        georef.add(new GCPoint(bottomright, new Point(pixelsize.x, pixelsize.y)));
        georef.add(new GCPoint(topright, new Point(pixelsize.x, 0)));
        georef.add(new GCPoint(bottomleft, new Point(0, pixelsize.y)));

        MapInfoObject ret = new MapInfoObject();
        ret.evalGCP(georef, pixelsize.x, pixelsize.y, coordinateReferenceSystem[getCrs(maparea.getCenter())]);
        return ret;
    }
}

class MapServiceComparer implements Comparer {
    CWPoint centre;

    public MapServiceComparer(CWPoint centre) {
        this.centre = centre;
    }

    public int compare(Object one, Object two) {
        if ((!(one instanceof OnlineMapService)) || (!(two instanceof OnlineMapService))) {
            return 0;
        } else {
            OnlineMapService a = (OnlineMapService) one;
            OnlineMapService b = (OnlineMapService) two;
            boolean aIsIn = a.boundingBox.isInBound(centre);
            boolean bIsIn = b.boundingBox.isInBound(centre);
            if (aIsIn && bIsIn) {
                // vereinfachend in Relation zur BoundingBox Diagonalen vergleichen.
                double ad = a.boundingBox.getCenter().getDistance(centre) / a.boundingBox.topleft.getDistance(a.boundingBox.bottomright);
                double bd = b.boundingBox.getCenter().getDistance(centre) / b.boundingBox.topleft.getDistance(b.boundingBox.bottomright);
                return (int) ((ad - bd) * 1000);
            } else {
                if (aIsIn && !bIsIn) {
                    return -1;
                } else if (!aIsIn && bIsIn) {
                    return 1;
                }
                return 0;
            }
        }
    }
}
