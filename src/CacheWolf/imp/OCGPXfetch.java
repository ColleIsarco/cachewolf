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
package CacheWolf.imp;

import CacheWolf.MainForm;
import CacheWolf.Preferences;
import CacheWolf.utils.MyLocale;
import CacheWolf.utils.UrlFetcher;
import ewe.io.File;
import ewe.io.IOException;
import ewe.ui.FormBase;

public class OCGPXfetch {
    public static void doIt() {
        String hostname = Preferences.itself().lastOCSite;
        boolean oldDownloadAllOC = Preferences.itself().downloadAllOC;
        boolean onlyListedAtOC = false;
        ImportGui importGui = new ImportGui(MyLocale.getMsg(130, "Download from opencaching"), ImportGui.ALL | ImportGui.HOST, ImportGui.DESCRIPTIONIMAGE | ImportGui.SPOILERIMAGE | ImportGui.LOGIMAGE);
        boolean downloadPics = importGui.downloadDescriptionImages;
        importGui.missingCheckBox.setText(MyLocale.getMsg(164, "only listed at OC"));
        importGui.missingCheckBox.setState(onlyListedAtOC);
        if (importGui.execute() == FormBase.IDCANCEL) {
            return;
        }
        onlyListedAtOC = Preferences.itself().downloadAllOC;
        Preferences.itself().downloadAllOC = oldDownloadAllOC;
        if (importGui.domains.getSelectedItem() != null) {
            hostname = (String) importGui.domains.getSelectedItem();
            Preferences.itself().lastOCSite = hostname;
        }

        try {
            String address = "http://" + hostname + "/search.php?";
            address += "searchto=searchbyfinder"; // searchbydistance
            address += "&showresult=1&expert=0&sort=bydistance&orderRatingFirst=0";
            address += "&f_userowner=0&f_userfound=0&f_inactive=0&f_ignored=0";
            address += "&f_otherPlatforms="; // 0 = all 1 = nur OC
            if (onlyListedAtOC)
                address += "1";
            else
                address += "0";
            address += "&country=&difficultymin=0&difficultymax=0&terrainmin=0&terrainmax=0&cachetype=1;2;3;4;5;6;7;8;9;10&cachesize=1;2;3;4;5;6;7&cache_attribs=&cache_attribs_not=";
            address += "&logtype=1,7";
            address += "&utf8=1&output=gpx&zip=1";
            address += "&count=max";
            address += "&finder=" + Preferences.itself().myAlias;
            String tmpFile = MainForm.profile.dataDir + "dummy.zip";
            login();
            UrlFetcher.fetchDataFile(address, tmpFile);
            File ftmp = new File(tmpFile);
            if (ftmp.exists() && ftmp.length() > 0) {
                GPXImporter gpx = new GPXImporter(tmpFile);
                if (downloadPics)
                    gpx.doIt(GPXImporter.DOLOADPICTURES);
                else
                    gpx.doIt(GPXImporter.DONOTLOADPICTURES);
            }
            ftmp.delete();
        } catch (IOException e) {
        }
    }

    public static boolean login() {
        // TODO this is only a preliminary Version of login
        // todo for other opencaching sites
        boolean loggedIn = false;
        String page;
        try {
            String loginDaten = "target=myhome.php&action=login&email=" + Preferences.itself().myAlias + "&password=" + Preferences.itself().password;
            UrlFetcher.setpostData(loginDaten);
            page = UrlFetcher.fetch("http://www.opencaching.de/login.php");
            // final PropertyList pl = UrlFetcher.getDocumentProperties();
            page = UrlFetcher.fetch("http://www.opencaching.de/myhome.php");
            loggedIn = page.indexOf("Eingeloggt als") > -1;
        } catch (IOException e) {
            Preferences.itself().log("Fehler", e);
        }
        return loggedIn;
    }
}
