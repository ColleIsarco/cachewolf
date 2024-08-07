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
package CacheWolf.controls;

import CacheWolf.Preferences;
import ewe.fx.Graphics;
import ewe.fx.IImage;
import ewe.fx.IconAndText;
import ewe.fx.Image;
import ewe.io.File;
import ewe.io.FileBase;
import ewe.io.IOException;
import ewe.sys.Process;
import ewe.sys.Vm;
import ewe.ui.*;

/**
 * hold preloaded versions of GUI images in a single place
 * <p>
 * Do not instantiate this class, only use it in a static way.
 */
public final class GuiImageBroker {
    private static final String basedir = FileBase.getProgramDirectory() + "/symbols/";

    private static final String INKSCAPE = "inkscape";

    public static Image found;
    public static Image disabled;
    public static Image archived;
    public static Image solved;
    public static Image bonus;
    public static Image owned;
    public static Image dnf;
    private static boolean useBigIcons;
    private static boolean useText = true;
    private static boolean useIcons = true;
    private static boolean leftIcons = false;
    private static Boolean inkscapeFound = null;

    private static void log(String text) {
        Preferences.itself().log(text); // log should work without reading preferences
    }

    /**
     *
     */
    public static void init(boolean useText, boolean useIcons, boolean useBigIcons, boolean leftIcons) {
        GuiImageBroker.useText = useText;
        GuiImageBroker.useIcons = useIcons;
        GuiImageBroker.leftIcons = leftIcons;
        GuiImageBroker.useBigIcons = useBigIcons;
        if (found == null) {
            found = getCacheTypeImage("found");
            disabled = getCacheTypeImage("disabled");
            archived = getCacheTypeImage("archived");
            solved = getCacheTypeImage("solved");
            bonus = getCacheTypeImage("bonus");
            owned = getCacheTypeImage("owned");
            dnf = getCacheTypeImage("dnf");
        }
    }

    private static String getCacheTypeImageName(String icon) {
        String in;
        File f = new File(basedir + icon + ".png");
        if (f.exists()) {
            in = f.getAbsolutePath();
        }
	else {
            in = icon + ".png";
        }
        return in;
    }

    public static Image getCacheTypeImage(String icon) {
        return new Image(getCacheTypeImageName(icon));
    }

    private static String getMapCacheTypeImageName(String icon) {
        String in;
        File f = new File(basedir + icon + "_map.png");
        if (f.exists()) {
            in = f.getAbsolutePath();
        } else {
            in = "map/" + icon + "_map.png";
        }
        return in;
    }

    public static Image getMapCacheTypeImage(String icon) {
        return new Image(getMapCacheTypeImageName(icon));
    }

    private static String getImageName(String icon) {
        String in;
        String extension = useBigIcons ? "_vga.png" : ".png";

        File f = new File(basedir + icon + extension);
        if (f.exists()) {
            in = f.getAbsolutePath();
        } else {
            in = icon + extension;
            String platform = Vm.getPlatform();
            boolean useSvg2png = false;
            try {
                int height = Preferences.itself().fontSize;
                if (useBigIcons) height *= 2;
                int width = height;
                File f1 = new File(FileBase.getProgramDirectory() + "/svg/Button/" + icon + ".svg");
                File f2 = new File(FileBase.getProgramDirectory() + "/svg/Cachetype/" + icon + ".svg");
                File f3 = new File(FileBase.getProgramDirectory() + "/svg/Size/" + icon + ".svg");
                File f4 = new File(FileBase.getProgramDirectory() + "/svg/Star/" + icon + ".svg");
                File f5 = new File(FileBase.getProgramDirectory() + "/svg/Waypoint/" + icon + ".svg");
                File sourceFile;
                if (f1.exists()) {
                    sourceFile = f1;
                } else if (f2.exists()) {
                    sourceFile = f2;
                } else if (f3.exists()) {
                    sourceFile = f3;
                } else if (f4.exists()) {
                    sourceFile = f4;
                    width = 5 * width;
                } else if (f5.exists()) {
                    sourceFile = f5;
                } else {
                    sourceFile = null;
                }
                Preferences.itself().log("Icon generator converts: " + sourceFile + " to: " + f.getAbsolutePath());
                if (System.getProperty("os.name") != null) {
                    if (System.getProperty("os.name").indexOf("indows") > -1) {
                        useSvg2png = true;
                    }
                }
                if (useSvg2png) {
                    Process p = Vm.exec(new String[]{"./svg2png.exe", sourceFile.toString(), f.toString(), Integer.toString(width)});
                    p.waitFor();
                    in = f.getAbsolutePath();
                } else {
                    if ("Java".equals(platform) && isInkscapePresent()) {
                        Process p = Vm.exec(new String[]{INKSCAPE, "-z", "-h", Integer.toString(height), "-e", f.toString(), sourceFile.toString()});
                        p.waitFor();
                        in = f.getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                Preferences.itself().log("Can not convert svg to png");
                Vm.printStackTrace(e, Vm.err());
                //can not convert....
            }
        }
        return in;
    }

    private static boolean isInkscapePresent() {
        if (inkscapeFound == null) {
            //Check if this process executes, if yes, then the app is present
            try {
                Process p = Vm.exec(new String[]{INKSCAPE, "-z", "-V"});
                p.waitFor();
                inkscapeFound = Boolean.TRUE;
                Preferences.itself().log("Inkscape found!");
            } catch (IOException e) {
                Preferences.itself().log("Can not start inkscape.\nCan not convert svg to png");
                inkscapeFound = Boolean.FALSE;
            }
        }

        return inkscapeFound.booleanValue();
    }

    private static String getText(String text) {
        if (!useText) {
            text = "";
        }
        return text;
    }

    public static Image getImage(String icon) {
        if (useIcons)
            return new Image(getImageName(icon));
        else
            // simply using a small transparent image
            //return new Image(getImageName("leer"));
            return null;
    }

    public static IconAndText getIconAndText(String text, String icon) {
        return new IconAndText(getImage(icon), getText(text), null); //Gui.makeHot(text)
    }

    public static IImage makeImageForButton(mButton btn, String text, String icon) {
        if (btn.image != null) {
            if (btn.image instanceof IconAndText) {
                return getIconAndText(text, icon);
            } else {
                return getImage(icon);
            }
        }
        return null;
    }

    public static void setButtonText(mButton btn, String text) {
        if (btn.image == null || (btn.image != null && !(btn.image instanceof IconAndText))) {
            btn.setText(text);
        } else {
            IconAndText iat = (IconAndText) btn.image;
            // hack for force update text of IconAndText
            // to change, there must be a change (of mTA)
            int mTA = iat.multiLineTextAlignment;
            if (mTA == 0)
                mTA = Graphics.CENTER;
            else
                mTA = 0;
            iat.text = text;
            iat.changeTextPosition(iat.textPosition, mTA);
            // won't change back to original at the moment
            btn.repaint(); // still have to do a repaint after the change
        }
    }

    public static void setButtonIconAndText(mButton btn, String text, IImage iat) {
        if (btn.image == null || (btn.image != null && !(btn.image instanceof IconAndText)) || iat == null) {
            // text and image separate
            btn.setText(text);
            if (iat != null) {
                btn.image = iat;
            }
        } else {
            // iat is IconAndText (and text already changed)
            btn.image = iat;
        }
        btn.repaint(); // ?still have to do a repaint after the change
    }

    public static mButton getButton(String text, String icon) {
        mButton btn;
        if (icon.length() == 0) {
            btn = new mButton(text);
        } else {
            if (useIcons) {
                if (leftIcons) {
                    btn = new mButton(getText(text));
                    // Graphics.Up, Graphics.Down, Graphics.Right, Graphics.Left // über, unter, rechts, links vom Icon
                    btn.textPosition = Graphics.Right;
                    btn.image = getImage(icon);
                } else {
                    // Icons in the middle of the Button (as IconAndText)
                    btn = new mButton(getText(text), getImageName(icon), null);
                }
            } else {
                if (text.length() == 0) {
                    btn = new mButton("", getImageName(icon), null);
                } else {
                    btn = new mButton(getText(text));
                }
            }
        }
        //btn.backGround = Color.LightGreen;
        return btn;
    }

    public static MenuItem getMenuItem(String text, String icon) {
        MenuItem mi = new MenuItem().iconize(getText(text), getImage(icon), true);
        return mi;
    }

    public static PullDownMenu getPullDownMenu(String text, String icon, MenuItem[] menuItems) {
        PullDownMenu pdm;
        if (leftIcons) {
            pdm = new PullDownMenu(getText(text), new Menu(menuItems, null));
            pdm.image = getImage(icon);
            pdm.textPosition = Graphics.Right; // rechts vom Icon
        } else {
            pdm = new PullDownMenu("", new Menu(menuItems, null));
            pdm.image = GuiImageBroker.getIconAndText(text, icon);
        }
        pdm.modify(0, ControlConstants.DrawFlat | ControlConstants.MakeMenuAtLeastAsWide | ControlConstants.NoFocus);
        pdm.setBorder(ewe.ui.UIConstants.BDR_OUTLINE, 1);
        //pdm.backGround = Color.LightGreen;
        return pdm;
    }
}
