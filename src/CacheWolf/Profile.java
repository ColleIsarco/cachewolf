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
package CacheWolf;

import CacheWolf.controls.InfoBox;
import CacheWolf.database.BoundingBox;
import CacheWolf.database.CWPoint;
import CacheWolf.database.CacheDB;
import CacheWolf.database.CacheHolder;
import CacheWolf.navi.TransformCoordinates;
import CacheWolf.utils.*;

import ewe.io.*;
import ewe.io.PrintWriter;
import ewe.sys.Convert;
import ewe.sys.Handle;
import ewe.sys.Vm;
import ewe.ui.FormBase;
import ewe.ui.ProgressBarForm;
import ewe.util.Hashtable;

import com.stevesoft.ewe_pat.Regex;
/**
 * This class holds a profile, i.e. a group of caches with a centre location
 *
 * @author salzkammergut
 */
public class Profile {
    public final static boolean SHOW_PROGRESS_BAR = true;
    public final static boolean NO_SHOW_PROGRESS_BAR = false;
    public final static boolean FORCESAVE = true;
    public final static boolean NOFORCESAVE = false;
    /**
     * version number of current format for index.xml and waypoint.xml
     */
    public static int CURRENTFILEFORMAT = 4;
    /**
     * The list of caches (CacheHolder objects).<br>
     * A pointer to this object exists in many classes in parallel to this object,<br>
     * i.e. the respective class contains both a {@link Profile} object and a cacheDB Vector.
     */
    public CacheDB cacheDB = new CacheDB();
    /**
     * The center point of this group of caches.<br>
     * Read from and stored to index.xml file<br>
     */
    public CWPoint center = new CWPoint();
    /**
     * The name of the profile.<br>
     * The baseDir in preferences is appended this name to give the dataDir where the index.xml and cache files live.
     */
    public String name = "";
    /**
     * This is the directory for the profile. It contains a closing /.
     */
    public String dataDir = "";
    public boolean selectionChanged = true; // ("Häckchen") used by movingMap to get to knao if it should update the caches in the map
    public boolean byPassIndexActive = false;
    public int numCachesInArea; // only valid after calling getSourroundingArea
    // Profile Settings
    private int indexXmlVersion;
    private FilterData currentFilter = new FilterData();
    private int filterActive = Filter.FILTER_INACTIVE;
    private boolean filterInverted = false;
    private boolean showBlacklisted = false;
    private boolean showSearchResult = false;
    private String timeZoneOffset = "0";
    private boolean timeZoneAutoDST = false;
    /**
     * True if the profile has been modified and not saved The following modifications set this flag: New profile centre, Change of waypoint data
     */
    private boolean hasUnsavedChanges = false;
    /**
     * Last sync date for opencaching caches
     */
    private String last_sync_opencaching = "";
    /**
     * Distance for opencaching caches
     */
    private String distOC = "";
    /**
     * path (there may be subdirs) to the maps of the profile, relative to the preferences maps dir with ending /
     */
    private String relativeMapsDir = "";
    /**
     * Distance for geocaching caches
     */
    private String distGC = "";
    private String minDistGC = "";
    private int lastUsedGpxStyle = 0;
    private int lastUsedOutputStyle = 0;
    private String gpxOutputTo = "";

    /**
     * Constructor for a profile
     */
    public Profile() {
    }

    /**
     * Returns <code>true</code> if profile needs to be changed when profile is left. Returns <code>false</code> if no relevant changes have been made.
     *
     * @return hasUnsavedChanges
     */
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }

    /**
     * Remember that profile needs to be saved. Flag is set <code>true</code> when parameter is true, but it's not set to <code>false</code> when parameter is <code>false</code>.<br>
     * This is only done internally on saving the cache.
     *
     * @param hasUnsavedChanges the hasUnsavedChanges to set
     */
    public void notifyUnsavedChanges(boolean changes) {
        hasUnsavedChanges = hasUnsavedChanges || changes;
    }

    public void resetUnsavedChanges() {
        hasUnsavedChanges = false;
    }

    public void clearProfile() {
        CacheHolder.removeAllDetails();
        cacheDB.clear();
        center.set(-361, -361);
        name = "";
        dataDir = "";
        setLast_sync_opencaching("");
        setDistOC("");
        setDistGC("");
        setMinDistGC("");
        setLastUsedGpxStyle(0);
        relativeMapsDir = "";
        resetUnsavedChanges();
    }

    /**
     * Method to save the index.xml file that holds the total information on available caches in the database. The database is nothing else than the collection of caches in a directory.
     * <p>
     * Not sure whether we need to keep 'pref' in method signature. May eventually remove it.
     * <p>
     * Saves the index with the filter settings from Filter
     */
    // public void saveIndex(Preferences pref, boolean showprogress){
    // saveIndex(pref,showprogress, Filter.filterActive,Filter.filterInverted);
    // }
    public void setCenterCoords(CWPoint coords) {
        this.notifyUnsavedChanges(coords.equals(this.center));
        this.center.set(coords);
    }

    /**
     * Save index with filter settings given
     */
    public void saveIndex(boolean showprogress, boolean forceSave) {
        if (hasUnsavedChanges || forceSave) {
            ProgressBarForm pbf = new ProgressBarForm();
            Handle h = new Handle();
            int updFrequ = Vm.isMobile() ? 10 : 40; // Number of caches between screen updates
            if (showprogress) {
                pbf.showMainTask = true;// false;
                pbf.setTask(h, "Saving Index");
                pbf.exec();
            }
            CacheHolder.saveAllModifiedDetails(); // this must be called first as it makes some calculations
            PrintWriter cacheDBIndexFile;
            CacheHolder ch;
            try {
                File backup = new File(dataDir + "index.bak");
                if (backup.exists()) {
                    backup.delete();
                }
                File index = new File(dataDir + "index.xml");
                index.rename("index.bak");
            } catch (Exception ex) {
                Preferences.itself().log("[Profile:saveIndex]Error deleting backup or renaming index.xml");
            }
            try {
                cacheDBIndexFile = new PrintWriter(new BufferedWriter(new FileWriter(new File(dataDir + "index.xml").getAbsolutePath())));
            } catch (Exception e) {
                Preferences.itself().log("Problem creating index.xml " + dataDir, e);
                return;
            }
            CWPoint savedCentre = center;
            if (center == null || !center.isValid() || (savedCentre.latDec == 0.0 && savedCentre.lonDec == 0.0))
                savedCentre = Preferences.itself().curCentrePt;

            try {
                cacheDBIndexFile.print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                cacheDBIndexFile.print("<CACHELIST format=\"decimal\">\n");
                cacheDBIndexFile.print("    <VERSION value = \"" + Profile.CURRENTFILEFORMAT + "\"/>\n");
                if (savedCentre.isValid())
                    cacheDBIndexFile.print("    <CENTRE lat=\"" + savedCentre.latDec + "\" lon=\"" + savedCentre.lonDec + "\"/>\n");
                if (last_sync_opencaching == null || last_sync_opencaching.endsWith("null") || last_sync_opencaching.equals("")) {
                    last_sync_opencaching = "20050801000000";
                }
                if (distOC == null || distOC.endsWith("null") || distOC.equals("")) {
                    distOC = "0.0";
                }
                if (distGC == null || distGC.endsWith("null") || distGC.equals("")) {
                    distGC = "0.0";
                }
                if (minDistGC == null || minDistGC.endsWith("null") || minDistGC.equals("")) {
                    minDistGC = "0.0";
                }

                // If the current filter is a CacheTour filter, then save it as
                // normal filter, because after loading there is no cache tour defined
                // which could be used as filter criterium.
                int activeFilterForSave;
                if (filterActive == Filter.FILTER_CACHELIST) {
                    activeFilterForSave = Filter.FILTER_ACTIVE;
                } else {
                    activeFilterForSave = filterActive;
                }
                cacheDBIndexFile.print("    <FILTERCONFIG status = \"" + activeFilterForSave + (filterInverted ? "T" : "F") + "\" showBlacklist = \"" + showBlacklisted + "\" />\n");
                cacheDBIndexFile.print(currentFilter.toXML(""));
                cacheDBIndexFile.print("    <SYNCOC date = \"" + last_sync_opencaching + "\" dist = \"" + distOC + "\"/>\n");
                cacheDBIndexFile.print("    <SPIDERGC dist = \"" + distGC + "\" mindist = \"" + minDistGC + "\"/>\n");
                cacheDBIndexFile.print("    <EXPORT style = \"" + lastUsedOutputStyle + lastUsedGpxStyle + "\" to = \"" + gpxOutputTo + "\"/>\n");
                cacheDBIndexFile.print("    <mapspath relativeDir = \"" + SafeXML.string2Html(relativeMapsDir) + "\"/>\n");
                cacheDBIndexFile.print("    <TIMEZONE timeZoneOffset = \"" + timeZoneOffset + "\" timeZoneAutoDST = \"" + timeZoneAutoDST + "\"/>\n");
                int size = cacheDB.size();
                for (int i = 0; i < size; i++) {
                    if (showprogress) {
                        h.progress = (float) i / (float) size;
                        if ((i % updFrequ) == 0)
                            h.changed();
                    }
                    ch = cacheDB.get(i);
                    if (ch.getCode().length() > 0) {
                        cacheDBIndexFile.print(ch.toXML());
                    }
                }
                cacheDBIndexFile.print("</CACHELIST>\n");
                cacheDBIndexFile.close();
                buildReferences(); // TODO Why is this needed here?
                if (showprogress)
                    pbf.exit(0);
            } catch (Exception e) {
                Preferences.itself().log("Problem writing to index file ", e);
                cacheDBIndexFile.close();
                if (showprogress)
                    pbf.exit(0);
            }
            hasUnsavedChanges = false;
        }
    }

    /**
     * Method to read the index.xml file that holds the total information on available caches in the database.<br>
     * The database in nothing else than the collection of caches in a directory.
     */
    public void readIndex(InfoBox infoBox, String dataDir) {
        this.dataDir = dataDir.replace('\\', '/');
        if (this.dataDir.endsWith("/"))
            this.dataDir = this.dataDir.substring(0, this.dataDir.length() - 1);
        this.name = this.dataDir.substring(this.dataDir.lastIndexOf("/") + 1);
        this.dataDir += '/';

        int updFrequ = Vm.isMobile() ? 10 : 40; // Number of caches between screen updates
        try {
            selectionChanged = true;
            boolean fmtDec = false;
            String mainInfoText = MyLocale.getMsg(5000, "Loading Cache-List");
            int wptNo = 1;
            int lastShownWpt = 0;
            char decSep = Common.getDigSeparator();
            char notDecSep = decSep == '.' ? ',' : '.';
            File indexFile = new File(this.dataDir + "index.xml");
            FileReader in = new FileReader(indexFile.getAbsolutePath());
            indexXmlVersion = 1; // Initial guess
            in.readLine(); // <?xml version= ...
            String text = in.readLine(); // <CACHELIST>
            if (text != null && text.indexOf("decimal") > 0)
                fmtDec = true;
            Extractor ex = new Extractor(null, " = \"", "\" ", 0, true);

            // ewe.sys.Time startT=new ewe.sys.Time();
            boolean convertWarningAlreadyDisplayed = false;
            while ((text = in.readLine()) != null) {
                // Check for Line with cache data
                if (text.indexOf("<CACHE ") >= 0) {
                    if (indexXmlVersion < CURRENTFILEFORMAT && !convertWarningAlreadyDisplayed) {
                        if (indexXmlVersion < CURRENTFILEFORMAT) {
                            convertWarningAlreadyDisplayed = true;
                            int res = new InfoBox(MyLocale.getMsg(144, "Warning"),
                                    MyLocale.getMsg(4407,
                                            "The profile files are not in the current format.%0aTherefore they are now converted to the current format. Depending of the size of the profile and the computer involved this may take some minutes. Please bear with us until the conversion is done."))
                                    .wait(FormBase.YESB | FormBase.NOB);
                            if (res == FormBase.IDNO) {
                                ewe.sys.Vm.exit(0);
                            }
                        }
                    }
                    if (infoBox != null) {
                        if (wptNo - updFrequ >= lastShownWpt) {
                            infoBox.setInfo(mainInfoText + "\n" + String.valueOf(wptNo));
                            lastShownWpt = wptNo;
                        }
                        wptNo++;
                    }
                    CacheHolder ch = CacheHolder.fromString(text, indexXmlVersion);
                    cacheDB.add(ch);
                } else if (text.indexOf("<CENTRE") >= 0) { // lat= lon=
                    Hashtable attributes = XMLParser.getAttributes(text, "CENTRE");
                    if (fmtDec) {
                        String lat = ((String) attributes.get("lat")).replace(notDecSep, decSep);
                        String lon = ((String) attributes.get("lon")).replace(notDecSep, decSep);
                        center.set(Convert.parseDouble(lat), Convert.parseDouble(lon));
                    } else {
                        int start = text.indexOf("lat=\"") + 5;
                        String lat = SafeXML.html2iso8859s1(text.substring(start, text.indexOf("\"", start)));
                        start = text.indexOf("long=\"") + 6;
                        String lon = SafeXML.html2iso8859s1(text.substring(start, text.indexOf("\"", start)));
                        center.set(lat + " " + lon, TransformCoordinates.DMM); // Fast parse
                    }
                } else if (text.indexOf("<VERSION") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "VERSION");
                    indexXmlVersion = Integer.valueOf((String) attributes.get("value")).intValue();
                    if (indexXmlVersion > CURRENTFILEFORMAT) {
                        Preferences.itself().log("[Profile:readIndex]unsupported file format");
                        clearProfile();
                        return;
                    }
                } else if (text.indexOf("<SYNCOC") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "SYNCOC");
                    last_sync_opencaching = (String) attributes.get("date");
                    distOC = (String) attributes.get("dist");
                } else if (text.indexOf("mapspath") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "mapspath");
                    String relativeDir = (String) attributes.get("relativeDir");
                    this.relativeMapsDir = (SafeXML.html2iso8859s1(relativeMapsDir).replace('\\', '/'));
                } else if (text.indexOf("<SPIDERGC") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "SPIDERGC");
                    distGC = (String) attributes.get("dist");
                    minDistGC = (String) attributes.get("mindist");
                    if (minDistGC == null) {
                        minDistGC = "0";
                    }
                } else if (text.indexOf("<EXPORT") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "EXPORT");
                    String style = (String) attributes.get("style");
                    String to = (String) attributes.get("to");
                    if (style == null) {
                        lastUsedGpxStyle = 0;
                        lastUsedOutputStyle = 0;
                    } else {
                        if (style.length() == 1) {
                            switch (Common.parseInt(style)) {
                                case 0:
                                    this.lastUsedGpxStyle = 0;
                                    this.lastUsedOutputStyle = 0;
                                    break;
                                case 1:
                                    this.lastUsedGpxStyle = 0;
                                    this.lastUsedOutputStyle = 1;
                                    break;
                                case 2:
                                    this.lastUsedGpxStyle = 0;
                                    this.lastUsedOutputStyle = 2;
                                    break;
                                case 3:
                                    this.lastUsedGpxStyle = 1;
                                    this.lastUsedOutputStyle = 0;
                                    break;
                                case 4:
                                    this.lastUsedGpxStyle = 2;
                                    this.lastUsedOutputStyle = 0;
                                    break;
                                default:
                                    this.lastUsedGpxStyle = 0;
                                    this.lastUsedOutputStyle = 0;
                                    break;
                            }
                        } else {
                            this.lastUsedOutputStyle = Common.parseInt(style.substring(0, 1));
                            this.lastUsedGpxStyle = Common.parseInt(style.substring(1, 2));
                        }
                    }
                    if (to == null) {
                        this.gpxOutputTo = "";
                    } else
                        this.gpxOutputTo = to;
                } else if (text.indexOf("<TIMEZONE") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "TIMEZONE");
                    timeZoneOffset = (String) attributes.get("timeZoneOffset");
                    String tmp = (String) attributes.get("timeZoneAutoDST");
                    if (timeZoneOffset == null) {
                        timeZoneOffset = "0";
                    }
                    if (tmp == null) {
                        timeZoneAutoDST = false;
                    } else {
                        timeZoneAutoDST = Boolean.valueOf(tmp).booleanValue();
                    }
                } else if (indexXmlVersion <= 2 && text.indexOf("<FILTER") >= 0) {
                    // Read filter data of file versions 1 and 2. (Legacy code)
                    String temp = ex.findFirst(text.substring(text.indexOf("<FILTER"))); // Filter status is now first, need to deal with old versions which don't have filter status
                    if (temp.length() == 2) {
                        // Compatibility with previous versions
                        if (temp.charAt(0) == 'T')
                            setFilterActive(Filter.FILTER_ACTIVE);
                        else
                            setFilterActive(Common.parseInt(temp.substring(0, 1)));
                        setFilterInverted(temp.charAt(1) == 'T');
                        setFilterRose(ex.findNext());
                    } else
                        setFilterRose(temp);
                    setFilterType(ex.findNext());
                    setFilterVar(ex.findNext());
                    setFilterDist(ex.findNext());
                    setFilterDiff(ex.findNext());
                    setFilterTerr(ex.findNext());
                    setFilterSize(ex.findNext());
                    String attr = ex.findNext();
                    long[] filterAttr = {0l, 0l, 0l, 0l};
                    if (attr != null && !attr.equals(""))
                        filterAttr[0] = Convert.parseLong(attr);
                    attr = ex.findNext();
                    if (attr != null && !attr.equals(""))
                        filterAttr[2] = Convert.parseLong(attr);
                    attr = ex.findNext();
                    setFilterAttr(filterAttr);
                    if (attr != null && !attr.equals(""))
                        setFilterAttrChoice(Convert.parseInt(attr));
                    setShowBlacklisted(Boolean.valueOf(ex.findNext()).booleanValue());
                } else if (text.indexOf("<FILTERDATA") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "FILTERDATA");
                    String filterRose = (String) attributes.get("rose");
                    String filterType = (String) attributes.get("type");
                    String filterVar = (String) attributes.get("var");
                    String filterDist = (String) attributes.get("dist");
                    String filterDiff = (String) attributes.get("diff");
                    String filterTerr = (String) attributes.get("terr");
                    String filterSize = (String) attributes.get("size");
                    String filterAttributesYes = (String) attributes.get("attributesYes");
                    String filterAttributesNo = (String) attributes.get("attributesNo");
                    String filterAttributesChoice = (String) attributes.get("attributesChoice");

                    String filterStatus = (String) attributes.get("status");
                    String filterUseRegexp = (String) attributes.get("useRegexp");
                    String filterNoCoord = (String) attributes.get("noCoord");

                    String filterAttributesYes1 = (String) attributes.get("attributeYes1");
                    String filterAttributesNo1 = (String) attributes.get("attributesNo1");
                    String filterSearch = (String) attributes.get("search");

                    setFilterRose(filterRose);
                    setFilterType(filterType);
                    setFilterVar(filterVar);
                    setFilterDist(filterDist);
                    setFilterDiff(filterDiff);
                    setFilterTerr(filterTerr);
                    setFilterSize(filterSize);
                    String attr = filterAttributesYes;
                    long[] filterAttr = {0l, 0l, 0l, 0l};
                    if (attr != null && !attr.equals(""))
                        filterAttr[0] = Convert.parseLong(attr);
                    attr = filterAttributesNo;
                    if (attr != null && !attr.equals(""))
                        filterAttr[2] = Convert.parseLong(attr);
                    setFilterAttr(filterAttr);
                    setFilterAttrChoice(Convert.parseInt(filterAttributesChoice));
                    setFilterStatus(SafeXML.html2iso8859s1(filterStatus));
                    setFilterUseRegexp(Boolean.valueOf(filterUseRegexp).booleanValue());
                    attr = filterNoCoord;
                    if (attr != null && !attr.equals("")) {
                        setFilterNoCoord(Boolean.valueOf(attr).booleanValue());
                    } else {
                        setFilterNoCoord(true);
                    }
                    attr = filterAttributesYes1;
                    if (attr != null && !attr.equals(""))
                        filterAttr[1] = Convert.parseLong(attr);
                    attr = filterAttributesNo1;
                    if (attr != null && !attr.equals(""))
                        filterAttr[3] = Convert.parseLong(attr);
                    setFilterAttr(filterAttr);

                    // Order within the search items must not be changed
                    attr = SafeXML.html2iso8859s1(filterSearch);
                    String[] searchFilterList = ewe.util.mString.split(attr, '|'); //'\u0399');
                    for (int i = 0; i < searchFilterList.length; i++) {
                        if (i == 0)
                            setFilterSyncDate(searchFilterList[i]);
                        if (i == 1)
                            setFilterNamePattern(searchFilterList[i]);
                        if (i == 2) {
                            if (searchFilterList[i].length() >= 2) {
                                this.setFilterNameCompare(Integer.parseInt(searchFilterList[i].substring(0, 1)));
                                this.setFilterNameCaseSensitive(searchFilterList[i].substring(1, 2).equals("0") ? false : true);
                            }
                        }
                    }

                } else if (text.indexOf("<FILTERCONFIG") >= 0) {
                    Hashtable attributes = XMLParser.getAttributes(text, "FILTERCONFIG");
                    String temp = (String) attributes.get("status");
                    setFilterActive(Common.parseInt(temp.substring(0, 1)));
                    setFilterInverted(temp.charAt(1) == 'T');
                    String value = (String) attributes.get("showBlacklist");
                    setShowBlacklisted(Boolean.valueOf(value).booleanValue());
                }
            }
            in.close();
            // Build references between caches and addi wpts
            if (infoBox != null) {
                infoBox.setInfo(MyLocale.getMsg(5004, "Building references..."));
            }
            buildReferences();
            if (indexXmlVersion < CURRENTFILEFORMAT) {
                saveIndex(Profile.SHOW_PROGRESS_BAR, Profile.FORCESAVE);
            }
        } catch (FileNotFoundException e) {
            Preferences.itself().log("index.xml not found in directory " + this.dataDir, e);
        } catch (IOException e) {
            Preferences.itself().log("Problem reading index.xml in dir: " + this.dataDir, e, true);
        }
        currentFilter.normaliseFilters();
        resetUnsavedChanges();
    }

    /**
     * Restore the filter to the values stored in this profile Called from Main Form and MainMenu The values of Filter.isActive and Filter.isInactive are set by the filter
     */
    public void restoreFilter() {
        restoreFilter(true);
    }

    void restoreFilter(boolean clearIfInactive) {
        boolean inverted = isFilterInverted(); // Save it as doFilter will clear filterInverted
        Filter flt = new Filter();
        if (filterActive == Filter.FILTER_ACTIVE) {
            flt.setFilter();
            flt.doFilter();
            if (inverted) {
                flt.invertFilter();
                setFilterInverted(true); // Needed because previous line inverts filterInverted
            }
        } else if (filterActive == Filter.FILTER_CACHELIST) {
            MainForm.itself.getCacheTour().applyCacheList();
            // flt.filterActive=filterActive;
        } else if (filterActive == Filter.FILTER_INACTIVE) {
            if (clearIfInactive) {
                flt.clearFilter();
            }
        }
    }

    public int getCacheIndex(String wp) {
        return cacheDB.getIndex(wp);
    }

    /**
     * Get a unique name for a new waypoint
     */
    public String getNewWayPointName(String prefix) {
        String strWp = null;
        long lgWp = 0;
        int s = cacheDB.size();
        if (s == 0)
            return prefix + "0000";
        // Create new waypoint,look if not in db
        do {
            lgWp++;
            strWp = prefix + MyLocale.formatLong(lgWp, "0000");
        } while (cacheDB.getIndex(strWp) >= 0);
        return strWp;
    }

    /**
     * @param forcache maincache
     * @return
     */
    public String getNewAddiWayPointName(String forcache) {
        int wptNo = -1;
        String waypoint;
        do {
            waypoint = MyLocale.formatLong(++wptNo, "00") + forcache.substring(2);
        } while (cacheDB.getIndex(waypoint) >= 0);
        return waypoint;
    }

    /**
     * Call this after getNewAddiWayPointName to set the references between main and addi correctly
     *
     * @param ch
     */
    public void setAddiRef(CacheHolder ch) {
	String mainwpt;
	int mainindex;
	Regex ocPattern = new Regex ("(OC.+)(-[0-9]+)");
	if (ocPattern.search(ch.getCode())){
	    mainwpt = ocPattern.stringMatched(1);
	    mainindex = cacheDB.getIndex(mainwpt);
	}
	else{
	    mainwpt = ch.getCode().substring(2);
	    mainindex = cacheDB.getIndex("GC" + mainwpt);
	}
	
        if (mainindex < 0 || !cacheDB.get(mainindex).isCacheWpt()) {
            for (int i = 0; i < OC.OCSites.length; i++) {
                mainindex = cacheDB.getIndex(OC.OCSites[i][OC.OC_PREFIX] + mainwpt);
                if (mainindex >= 0 && cacheDB.get(mainindex).isCacheWpt()) {
                    break;
                }
            }
        }
        if (mainindex < 0 || !cacheDB.get(mainindex).isCacheWpt()){
            mainindex = cacheDB.getIndex("CW" + mainwpt);
	}

        if (mainindex < 0) {
            ch.setIncomplete(true);
        }
	else {
            CacheHolder mainch = cacheDB.get(mainindex);
            if (mainch.getCode().equals(ch.getCode())) {
                ch.setIncomplete(true);
            }
	    else {
                mainch.addiWpts.add(ch);
                ch.mainCache = mainch;
                ch.setAttributesFromMainCache();
            }
        }
    }

    public String toString() {
        return "Profile: Name=" + name + "\nCentre=" + center.toString() + "\ndataDir=" + dataDir + "\nlastSyncOC=" + last_sync_opencaching + "\ndistOC=" + distOC + "\ndistGC=" + distGC;
    }

    /**
     * Sets the selection state for all caches to the given state <code>selectStatus</code>. There is a little distinction for the <code>true</code> and <code>false</code> case:<br>
     * selectStatus <code>true</code>: All <i>visible</i> caches are checked, and their addi wpts, regardless if they are visible or not.<br>
     * selectStatus <code>false</code>: All caches are unchecked, regardless if they are visible or not.
     *
     * @param selectStatus If <code>true</code> all caches are checked, if <code>false</code> all caches are unchecked.
     */
    public void setSelectForAll(boolean selectStatus) {
        CacheHolder ch;
        selectionChanged = true;
        for (int i = cacheDB.size() - 1; i >= 0; i--) {
            ch = cacheDB.get(i);
            if (selectStatus) {
                if (ch.isVisible()) {
                    ch.isChecked = selectStatus; // set the ceckbox also for addi wpts
                    if (ch.hasAddiWpt()) {
                        CacheHolder addiWpt;
                        int addiCount = ch.addiWpts.getCount();
                        for (int j = 0; j < addiCount; j++) {
                            addiWpt = (CacheHolder) ch.addiWpts.get(j);
                            addiWpt.isChecked = selectStatus;
                        }
                    }
                }
            } else /* selectStatus==false */ {
                ch.isChecked = selectStatus;
            }
        }
    }

    public BoundingBox getSourroundingArea(boolean onlyOfSelected) {
        if (cacheDB == null || cacheDB.size() == 0)
            return null;
        CacheHolder ch;
        CWPoint topleft = null;
        CWPoint bottomright = null;
        numCachesInArea = 0;
        boolean isAddi = false;
        for (int i = cacheDB.size() - 1; i >= 0; i--) {
            ch = cacheDB.get(i);
            if (!onlyOfSelected || ch.isChecked) {
                if (ch.getWpt().isValid()) { // done: && ch.pos.latDec != 0 && ch.pos.lonDec != 0 TO-DO != 0 sollte rausgenommen werden sobald in der Liste vernünftig mit nicht gesetzten pos umgegangen wird
                    isAddi = ch.isAddiWpt();
                    // test for plausiblity of coordinates of Additional Waypoints: more then 1000 km away from main Waypoint is unplausible ->
                    // ignore it //
                    // && ch.mainCache != null is only necessary because the data base may be corrupted
                    if (!isAddi || (isAddi && ch.mainCache != null && ch.getWpt().getDistance(ch.mainCache.getWpt()) < 1000)) {
                        if (topleft == null)
                            topleft = new CWPoint(ch.getWpt());
                        if (bottomright == null)
                            bottomright = new CWPoint(ch.getWpt());
                        if (topleft.latDec < ch.getWpt().latDec)
                            topleft.latDec = ch.getWpt().latDec;
                        if (topleft.lonDec > ch.getWpt().lonDec)
                            topleft.lonDec = ch.getWpt().lonDec;
                        if (bottomright.latDec > ch.getWpt().latDec)
                            bottomright.latDec = ch.getWpt().latDec;
                        if (bottomright.lonDec < ch.getWpt().lonDec)
                            bottomright.lonDec = ch.getWpt().lonDec;
                        numCachesInArea++;
                    }
                }
            }
        }
        if (topleft != null && bottomright != null)
            return new BoundingBox(topleft, bottomright);
        else
            return null;
    }

    /**
     * Method to calculate bearing and distance of a cache in the index list.
     *
     * @see CacheHolder
     * @see Extractor
     */
    public void updateBearingDistance() {
        CWPoint centerPoint = new CWPoint(Preferences.itself().curCentrePt); // Clone current centre to be sure
        int anz = cacheDB.size();
        CacheHolder ch;
        // Jetzt durch die CacheDaten schleifen
        while (--anz >= 0) {
            ch = cacheDB.get(anz); // This returns a pointer to the CacheHolder object
            ch.calcDistance(centerPoint);
        }
        // The following call is not very clean as it mixes UI with base classes
        // However, calling it from here allows us to recenter the
        // radar panel with only one call
        if (MainTab.itself != null)
            MainTab.itself.radarPanel.recenterRadar();
    } // updateBearingDistance

    /**
     * Method to build the reference between addi wpt and main cache.
     */
    public void buildReferences() {
        CacheHolder ch;
        MyComparer myComparer = new MyComparer();

        // Build index for faster search and clear all references
        for (int i = cacheDB.size() - 1; i >= 0; i--) {
            ch = cacheDB.get(i);
            ch.addiWpts.clear();
            ch.mainCache = null;
        }

        // Build references
        int max = cacheDB.size();
        for (int i = 0; i < max; i++) {
            ch = cacheDB.get(i);
            if (ch.isAddiWpt())
                setAddiRef(ch);
        }
        // sort addi wpts
        for (int i = 0; i < max; i++) {
            ch = cacheDB.get(i);
            if (ch.hasAddiWpt() && (ch.addiWpts.size() > 1)) {
                ch.addiWpts.sort(myComparer, false);
            }
        }

    }

    // Getter and Setter for private properties

    public String getFilterType() {
        return currentFilter.getFilterType();
    }

    public void setFilterType(String filterType) {
        this.notifyUnsavedChanges(!filterType.equals(this.getFilterType()));
        this.currentFilter.setFilterType(filterType);
    }

    public String getFilterRose() {
        return currentFilter.getFilterRose();
    }

    public void setFilterRose(String filterRose) {
        this.notifyUnsavedChanges(!filterRose.equals(this.getFilterRose()));
        this.currentFilter.setFilterRose(filterRose);
    }

    public String getFilterSize() {
        return currentFilter.getFilterSize();
    }

    public void setFilterSize(String filterSize) {
        this.notifyUnsavedChanges(!filterSize.equals(this.getFilterSize()));
        this.currentFilter.setFilterSize(filterSize);
    }

    public String getFilterVar() {
        return currentFilter.getFilterVar();
    }

    public void setFilterVar(String filterVar) {
        this.notifyUnsavedChanges(!filterVar.equals(this.getFilterVar()));
        this.currentFilter.setFilterVar(filterVar);
    }

    public String getFilterDist() {
        return currentFilter.getFilterDist();
    }

    public void setFilterDist(String filterDist) {
        this.notifyUnsavedChanges(!filterDist.equals(this.getFilterDist()));
        this.currentFilter.setFilterDist(filterDist);
    }

    public String getFilterDiff() {
        return currentFilter.getFilterDiff();
    }

    public void setFilterDiff(String filterDiff) {
        this.notifyUnsavedChanges(!filterDiff.equals(this.getFilterDiff()));
        this.currentFilter.setFilterDiff(filterDiff);
    }

    public String getFilterTerr() {
        return currentFilter.getFilterTerr();
    }

    public void setFilterTerr(String filterTerr) {
        this.notifyUnsavedChanges(!filterTerr.equals(this.getFilterTerr()));
        this.currentFilter.setFilterTerr(filterTerr);
    }

    public int getFilterActive() {
        return filterActive;
    }

    public void setFilterActive(int filterActive) {
        this.notifyUnsavedChanges(filterActive != this.filterActive);
        this.setFilterInverted(false);
        this.filterActive = filterActive;
    }

    public boolean isFilterInverted() {
        return filterInverted;
    }

    public void setFilterInverted(boolean filterInverted) {
        this.notifyUnsavedChanges(filterInverted != this.filterInverted);
        this.filterInverted = filterInverted;
    }

    public boolean showBlacklisted() {
        return showBlacklisted;
    }

    public void setShowBlacklisted(boolean showBlacklisted) {
        this.notifyUnsavedChanges(showBlacklisted != this.showBlacklisted);
        this.showBlacklisted = showBlacklisted;
    }

    /**
     * If <code>true</code> then the cache list will only display the caches that are result of a search.
     *
     * @return <code>True</code> if list should only display search results
     */
    public boolean showSearchResult() {
        return showSearchResult;
    }

    /**
     * Sets parameter if cache list should only display the caches that are results of a search.
     *
     * @param showSearchResult <code>True</code>: List should only display search results.
     */
    public void setShowSearchResult(boolean showSearchResult) {
        this.showSearchResult = showSearchResult;
    }

    public long[] getFilterAttr() {
        return currentFilter.getFilterAttr();
    }

    public void setFilterAttr(long[] filterAttr) {
        this.notifyUnsavedChanges(filterAttr != this.getFilterAttr());
        this.currentFilter.setFilterAttr(filterAttr);
    }

    public int getFilterAttrChoice() {
        return this.currentFilter.getFilterAttrChoice();
    }

    public void setFilterAttrChoice(int filterAttrChoice) {
        this.notifyUnsavedChanges(filterAttrChoice != this.getFilterAttrChoice());
        this.currentFilter.setFilterAttrChoice(filterAttrChoice);
    }

    public String getFilterStatus() {
        return currentFilter.getFilterStatus();
    }

    public void setFilterStatus(String filterStatus) {
        this.notifyUnsavedChanges(filterStatus != this.getFilterStatus());
        this.currentFilter.setFilterStatus(filterStatus);
    }

    public boolean getFilterUseRegexp() {
        return currentFilter.useRegexp();
    }

    public void setFilterUseRegexp(boolean useRegexp) {
        this.notifyUnsavedChanges(useRegexp != this.getFilterUseRegexp());
        this.currentFilter.setUseRegexp(useRegexp);
    }

    public boolean getFilterNoCoord() {
        return currentFilter.getFilterNoCoord();
    }

    public void setFilterNoCoord(boolean filterNoCoord) {
        this.notifyUnsavedChanges(filterNoCoord != this.getFilterNoCoord());
        this.currentFilter.setFilterNoCoord(filterNoCoord);
    }

    public String getLast_sync_opencaching() {
        return last_sync_opencaching;
    }

    public void setLast_sync_opencaching(String last_sync_opencaching) {
        this.notifyUnsavedChanges(!last_sync_opencaching.equals(this.last_sync_opencaching));
        this.last_sync_opencaching = last_sync_opencaching;
    }

    public String getFilterSyncDate() {
        return currentFilter.getSyncDate();
    }

    public void setFilterSyncDate(String lastDate) {
        this.notifyUnsavedChanges(lastDate != currentFilter.getSyncDate());
        this.currentFilter.setSyncDate(lastDate);
    }

    public String getFilterNamePattern() {
        return currentFilter.getNamePattern();
    }

    public void setFilterNamePattern(String pattern) {
        this.notifyUnsavedChanges(pattern != currentFilter.getNamePattern());
        this.currentFilter.setNamePattern(pattern);
    }

    public int getFilterNameCompare() {
        return currentFilter.getNameCompare();
    }

    public void setFilterNameCompare(int compare) {
        this.notifyUnsavedChanges(compare != currentFilter.getNameCompare());
        this.currentFilter.setNameCompare(compare);
    }

    public boolean getFilterNameCaseSensitive() {
        return currentFilter.getNameCaseSensitive();
    }

    public void setFilterNameCaseSensitive(boolean caseSensitiv) {
        this.notifyUnsavedChanges(caseSensitiv != currentFilter.getNameCaseSensitive());
        this.currentFilter.setNameCaseSensitive(caseSensitiv);
    }

    public String getDistOC() {
        return distOC;
    }

    public void setDistOC(String distOC) {
        this.notifyUnsavedChanges(!distOC.equals(this.distOC));
        this.distOC = distOC;
    }

    public String getDistGC() {
        return distGC;
    }

    public void setDistGC(String distGC) {
        this.notifyUnsavedChanges(!distGC.equals(this.distGC));
        this.distGC = distGC;
    }

    public String getMinDistGC() {
        return minDistGC;
    }

    public void setMinDistGC(String minDistGC) {
        this.notifyUnsavedChanges(!minDistGC.equals(this.minDistGC));
        this.minDistGC = minDistGC;
    }

    public int getProfilesLastUsedGpxStyle() {
        return lastUsedGpxStyle;
    }

    public void setLastUsedGpxStyle(int _style) {
        this.notifyUnsavedChanges(_style != this.lastUsedGpxStyle);
        this.lastUsedGpxStyle = _style;
    }

    public int getProfilesLastUsedOutputStyle() {
        return lastUsedOutputStyle;
    }

    public void setLastUsedOutputStyle(int _style) {
        this.notifyUnsavedChanges(_style != this.lastUsedOutputStyle);
        this.lastUsedOutputStyle = _style;
    }

    public String getGpxOutputTo() {
        return gpxOutputTo;
    }

    public void setGpxOutputTo(String value) {
        notifyUnsavedChanges(!value.equals(gpxOutputTo));
        gpxOutputTo = value;
    }

    public void setRelativeMapsDir(String relativeMapsDir) {
        this.notifyUnsavedChanges(!relativeMapsDir.equals(this.relativeMapsDir));
        this.relativeMapsDir = relativeMapsDir;
    }

    public String getMapsDir() {
        return Preferences.itself().absoluteMapsBaseDir + relativeMapsDir;
    }

    public String getMapsSubDir(String mapsDir) {
        mapsDir = STRreplace.replace(mapsDir, "\\", "/"); // da mapsDir aus Eingabe (fc) kommt
        if (!mapsDir.endsWith("/")) {
            mapsDir = mapsDir + "/";
        }
        String ret = Preferences.itself().absoluteMapsBaseDir;
        if (mapsDir.startsWith(ret)) {
            ret = mapsDir.substring(ret.length());
        } else
            ret = "";
        return ret;
    }

    public long getTimeZoneOffsetLong() {
        long offset = 0;
        if (timeZoneOffset.equalsIgnoreCase("auto")) {
            offset = 100;
        } else {
            try {
                offset = Convert.toInt(timeZoneOffset);
            } catch (Exception e) {
                offset = 0;
            }
        }
        return offset;
    }

    public String getTimeZoneOffset() {
        return timeZoneOffset;
    }

    public void setTimeZoneOffset(String offset) {
        this.notifyUnsavedChanges(!offset.equals(this.timeZoneOffset));
        this.timeZoneOffset = offset;
    }

    public boolean getTimeZoneAutoDST() {
        return timeZoneAutoDST;
    }

    public void setTimeZoneAutoDST(boolean autoDST) {
        this.notifyUnsavedChanges(autoDST != this.timeZoneAutoDST);
        this.timeZoneAutoDST = autoDST;
    }

    /**
     * Returns the currently active FilterData object for the profile.
     *
     * @return Object representing the setting of the filter
     */
    public FilterData getCurrentFilter() {
        return currentFilter;
    }

    public void setCurrentFilter(FilterData currentFilter) {
        this.currentFilter = currentFilter;
    }

    private class MyComparer implements ewe.util.Comparer {

        public int compare(Object o1, Object o2) {
            return ((CacheHolder) o1).getCode().compareTo(((CacheHolder) o2).getCode());
        }

    }
}
