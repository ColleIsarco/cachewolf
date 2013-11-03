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

import ewe.fx.Color;
import ewe.fx.DrawnIcon;
import ewe.fx.Font;
import ewe.fx.Graphics;
import ewe.fx.Rect;
import ewe.sys.Device;
import ewe.sys.Vm;
import ewe.ui.CellPanel;
import ewe.ui.Editor;
import ewe.ui.Event;
import ewe.ui.FormBase;
import ewe.ui.Gui;
import ewe.ui.MenuItem;
import ewe.ui.PanelSplitter;
import ewe.ui.SplittablePanel;
import ewe.ui.WindowConstants;
import ewe.ui.mApp;

/**
 * Mainform is responsible for building the user interface. Class ID = 5000
 */
public class MainForm extends Editor {
    public static MainForm itself;

    // The next three declares are for the cachelist
    public boolean cacheListVisible = false;
    public CacheList cacheList;
    SplittablePanel split;

    MainTab mainTab;
    MainMenu mainMenu;

    /**
     * Constructor for MainForm
     * <p>
     * Loads preferences and the cache index list. Then constructs a MainMenu and the tabbed Panel (MainTab). MainTab holds the different tab panels. MainMenu contains the menu entries.
     * 
     * @see MainMenu
     * @see MainTab
     */
    public MainForm(boolean dbg, String pathToPrefXml) {
	itself = this;
	// Resize the Close und Ok-Buttons of all Forms. This is just a test for the PDA Versions:
	int fontSize = Global.pref.fontSize; // constructor default value
	FormBase.close = new DrawnIcon(DrawnIcon.CROSS, fontSize, fontSize, new Color(0, 0, 0));
	FormBase.tick = new DrawnIcon(DrawnIcon.TICK, fontSize, fontSize, new Color(0, 128, 0));
	FormBase.cross = new DrawnIcon(DrawnIcon.CROSS, fontSize, fontSize, new Color(128, 0, 0));

	Global.pref.debug = dbg;
	// in case pathtoprefxml == null the preferences will determine the path itself
	Global.pref.setPathToConfigFile(pathToPrefXml);
	doIt();
    }

    public void doIt() {
	this.exitSystemOnClose = true;
	this.resizable = true;
	this.moveable = true;
	this.windowFlagsToSet = WindowConstants.FLAG_MAXIMIZE_ON_PDA;
	// if (ewe.ui.Gui.screenSize.width <= 350 && ewe.ui.Gui.screenSize.height <= 350)
	//Rect screen = ((ewe.fx.Rect) (Window.getGuiInfo(WindowConstants.INFO_SCREEN_RECT,null,new ewe.fx.Rect(),0)));
	//if ( screen.height >= 600 && screen.width >= 800) this.setPreferredSize(800, 600);
	this.resizeOnSIP = true;
	InfoBox infB = null;
	try {
	    Global.pref.readPrefFile();
	    Global.pref.logInit();
	    if (MyLocale.initErrors.length() != 0) {
		new InfoBox("Error", MyLocale.initErrors).wait(FormBase.OKB);
	    }
	    if (Vm.isMobile()) {
		//this.windowFlagsToSet |=Window.FLAG_FULL_SCREEN;
		this.resizable = false;
		this.moveable = false;
	    } else {
		int h, w;
		h = Global.pref.myAppHeight;
		if (h > MyLocale.getScreenHeight())
		    h = MyLocale.getScreenHeight();
		w = Global.pref.myAppWidth;
		if (w > MyLocale.getScreenWidth())
		    w = MyLocale.getScreenWidth();
		this.setPreferredSize(w, h);
	    }
	    addGuiFont();
	    // Replace buildt-in symbols with customized images (if there are some)
	    GuiImageBroker.customizedSymbols();

	    if (!Global.pref.selectProfile(Preferences.PROFILE_SELECTOR_ONOROFF, true))
		ewe.sys.Vm.exit(0); // User MUST select or create a profile
	    Vm.showWait(true);

	    // Load CacheList
	    infB = new InfoBox("CacheWolf", MyLocale.getMsg(5000, "Loading Cache-List"));
	    infB.exec();
	    infB.waitUntilPainted(100);
	    Global.profile.readIndex(infB);
	    setTitle(Global.profile.name + " - CW " + Version.getRelease());
	} catch (Exception e) {
	    Global.pref.log("[MainForm:DoIt]", e);
	}

	if (Gui.screenIs(Gui.PDA_SCREEN) && Vm.isMobile()) {
	    Vm.setSIP(Vm.SIP_LEAVE_BUTTON, mApp.mainApp);
	}
	if (Global.pref.fixSIP) {
	    if (Gui.screenIs(Gui.PDA_SCREEN) && Vm.isMobile()) {
		//Vm.setSIP(Vm.SIP_LEAVE_BUTTON|Vm.SIP_ON);
		Vm.setParameter(Vm.SET_ALWAYS_SHOW_SIP_BUTTON, 1);
		Device.preventIdleState(true);
	    }
	} else
	    Vm.setSIP(0);

	mainMenu = new MainMenu(this); // ctor prior to mainTab
	mainTab = new MainTab();
	Global.pref.setCurCentrePt(Global.profile.centre); //uses mainTab.tablePanel

	mainMenu.allowProfileChange(true);

	split = new SplittablePanel(PanelSplitter.HORIZONTAL);
	split.theSplitter.thickness = 0;
	split.theSplitter.modify(Invisible, 0);
	// CacheList for CacheTour
	CellPanel pnlCacheList = split.getNextPanel();
	CellPanel pnlMainTab = split.getNextPanel();
	split.setSplitter(PanelSplitter.MIN_SIZE | PanelSplitter.BEFORE, PanelSplitter.HIDDEN | PanelSplitter.BEFORE, PanelSplitter.CLOSED);

	pnlCacheList.addLast(cacheList = new CacheList(), STRETCH, FILL);
	pnlMainTab.addLast(mainTab, STRETCH, FILL);
	this.addLast(split, STRETCH, FILL);

	mainMenu.setTablePanel(mainTab.getTablePanel());
	if (infB != null)
	    infB.close(0);
	mainTab.tablePanel.refreshTable();
	mainTab.tablePanel.selectFirstRow();
	//mainTab.tablePanel.tc.paintSelection();
	Vm.showWait(false);
	this.firstFocus = mainTab.tablePanel.myTableControl; // works if tablePanel is the first screen
    }

    protected void checkButtons() {
	if (Global.pref.hasCloseButton)
	    super.checkButtons();
    }

    protected boolean canExit(int exitCode) {
	mainTab.saveUnsavedChanges(true);
	return Global.pref.hasCloseButton;
    }

    private void addGuiFont() {
	Font defaultGuiFont = mApp.findFont("gui");
	Font newGuiFont = new Font(Global.pref.fontName, defaultGuiFont.getStyle(), Global.pref.fontSize);
	mApp.addFont(newGuiFont, "gui");
	mApp.fontsChanged();
	mApp.mainApp.font = newGuiFont;
    }

    public void doPaint(Graphics g, Rect r) {
	Global.pref.myAppHeight = this.height;
	Global.pref.myAppWidth = this.width;
	super.doPaint(g, r);
    }

    public void toggleCacheListVisible() {
	cacheListVisible = !cacheListVisible;
	if (cacheListVisible) {
	    // Make the splitterbar visible with a width of 6
	    split.theSplitter.modify(0, Invisible);
	    split.theSplitter.resizeTo(6, split.theSplitter.getRect().height);
	    MainForm.itself.mainMenu.cacheTour.modifiers |= MenuItem.Checked;
	} else {
	    // Hide the splitterbar and set width to 0
	    split.theSplitter.modify(Invisible, 0);
	    split.theSplitter.resizeTo(0, split.theSplitter.getRect().height);
	    MainForm.itself.mainMenu.cacheTour.modifiers &= ~MenuItem.Checked;
	}
	split.theSplitter.doOpenClose(cacheListVisible);
	MainForm.itself.mainMenu.repaint();
    }

    public void onEvent(Event ev) { // Preferences have been changed by PreferencesScreen
	if (Global.pref.dirty) {
	    mainTab.getTablePanel().myTableModel.setColumnNamesAndWidths();
	    mainTab.getTablePanel().refreshControl();
	    Global.pref.dirty = false;
	}
	super.onEvent(ev);
    }

}
