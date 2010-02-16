package CacheWolf.navi.touchControls;

import CacheWolf.Global;
import CacheWolf.navi.MovingMap;
import ewe.fx.Dimension;
import ewe.fx.Point;
import ewe.fx.mImage;
import ewe.graphics.AniImage;
import ewe.sys.Vm;
import ewe.util.Hashtable;
import ewe.util.Vector;

/**
 * @author H�lmchen
 */

public class MovingMapControls implements ICommandListener {

	Vector buttons = null;

	private boolean vga;

	private MovingMap movingMap;

	// private TextImage iconHelp;

	private boolean helpIsVisible = false;

	private int lastTime = Vm.getTimeStamp();

	private Hashtable roles = new Hashtable();

	public MovingMapControls(MovingMap movingMap) {
		if (movingMap == null) {
			throw new IllegalArgumentException("moving map not set");
		}
		Vm.showWait(movingMap, true);
		this.vga = movingMap.isMobileVga();
		this.movingMap = movingMap;
		Dimension di = new Dimension();
		movingMap.getDisplayedSize(di);
		Vm.debug(di.height+" "+ di.width);
		MovingMapControlSettings movingMapControlSettings = new MovingMapControlSettings(
				vga, roles);
		
		Dimension dest= new Dimension();
		movingMap.getPreferredSize(dest);
		movingMapControlSettings.readFile(dest);
		buttons = movingMapControlSettings.getMenuItems();

		// create all needed Buttons
		setStateOfIcons();
		Vm.showWait(movingMap, false);
	}

	private void setStateOfIcons() {
		for (int i = 0; i < buttons.size(); i++) {
			MovingMapControlItem item = (MovingMapControlItem) buttons.get(i);
			AniImage ani = item.getImage();
			if (ani == null) {
				continue;
			}
			movingMap.getMmp().removeImage(ani);
			if (item.isVisible(roles)) {
				movingMap.getMmp().addImage(ani);
			}
		}
	}

	public boolean changeRoleState(String role) {
		Object object = roles.get(role);
		if (object == null) {
			return false;
		}
		Role r = (Role) object;
		if (r.getState() == Boolean.TRUE) {
			return changeRoleState(role,r,Boolean.FALSE);
		} else {
			return changeRoleState(role,r,Boolean.TRUE);
		}
	}
	
	public boolean changeRoleState(String role, Boolean b) {
		Object object = roles.get(role);
		if (object == null) {
			return false;
		}
		Role r = (Role) object;
		return changeRoleState(role,r,b);
		
	}
	
	private boolean  changeRoleState(String roleName , Role role, Boolean b) {
		role.setState(b);
		if (b == Boolean.TRUE) {
			String[] rToDis = role.getRolesToDisable();
			if (rToDis != null) {
				for (int i = 0; i < rToDis.length; i++) {
					String roleToDis = rToDis[i];
					changeRoleState(roleToDis, Boolean.FALSE);
				}
			}
		}
		setStateOfIcons();
		movingMap.repaintNow();
		
		return checkRolesForAction(roleName, b.booleanValue());
	}

	private boolean checkRolesForAction(String role, boolean state) {
		if (role==null) {
			return false;
		}
		if ("show_map".equals(role)) {
			if (state) {
				return movingMap.handleCommand(SHOW_MAP);
			}else
				return movingMap.handleCommand(HIDE_MAP);
		}
		if ("show_caches".equals(role)) {
			if (state) {
				return movingMap.handleCommand(SHOW_CACHES);
			}else
				return movingMap.handleCommand(HIDE_CACHES);
		}
		
		if ("fill_white".equals(role)) {
			if (state) {
				return movingMap.handleCommand(FILL_MAP);
			}else
				return movingMap.handleCommand(NO_FILL_MAP);
		}
		
		if ("zoom_manually".equals(role)) {
			if (state) {
				movingMap.setZoomingMode(true);
			}else
				movingMap.setZoomingMode(false);
			return true;
		}
		return false;
	}

//	private void checkStateOfIcon(MovingMapControlItem item, AniImage ani) {
//		if (MOVE_TO_DEST.equals(item.getActionCommand())) {
//			if (movingMap.getDestination() != null) {
//				ani.properties &= ~mImage.IsNotHot;
//			} else {
//				ani.properties |= mImage.IsNotHot;
//			}
//		}
//		if (MOVE_TO_CENTER.equals(item.getActionCommand())) {
//			if (Global.getPref().getCurCentrePt().isValid())
//				ani.properties &= ~mImage.IsNotHot;
//			else {
//				ani.properties |= mImage.IsNotHot;
//
//			}
//		}}

	public void updateContext(String contextName, String text) {
		if (contextName == null) {
			return;
		}
		updateContext(contextName, text, -1);

	}

	public void updateContext(String contextName, String text, int property) {
		for (int i = 0; i < buttons.size(); i++) {
			MovingMapControlItem item = (MovingMapControlItem) buttons.get(i);

			if ((item.xProperties & MovingMapControlItem.IS_ICON_WITH_TEXT) != 0) {
				if (contextName.equals(item.getContext())) {
					item.setText(text);
				}
			}
			if ((item.xProperties & MovingMapControlItem.IS_ICON_WITH_FRONTLINE) != 0) {
				if (contextName.equals(item.getContext())) {
					item.setAdditionalProperty(property);
				}
			}
		}

	}


	public void updateFormSize(int w, int h) {

		// adding bottom and top
		for (int i = 0; i < buttons.size(); i++) {
			MovingMapControlItem item = (MovingMapControlItem) buttons.get(i);

			if ((item.xProperties & MovingMapControlItem.IS_ICON_WITH_COMMAND) == 0
					&& (item.xProperties & MovingMapControlItem.IS_ICON_WITH_TEXT) == 0) {
				// System.out
				// .println(((item.xProperties &
				// MovingMapControlItem.IS_ICON_WITH_COMMAND) == 0)
				// + " "
				// + ((item.xProperties &
				// MovingMapControlItem.IS_ICON_WITH_TEXT) == 0));
			}

			AniImage ani = item.getImage();

			int xpos = 0;
			int ypos = 0;

			if ((item.xProperties & MovingMapControlItem.DISPLAY_FROM_TOP) != 0) {
				ypos = item.getyPos();
			} else
				ypos = h - item.getyPos();

			if ((item.xProperties & MovingMapControlItem.DISPLAY_FROM_LEFT) != 0) {

				xpos = item.getxPos();
			} else
				xpos = w - item.getxPos();

			ani.setLocation(xpos, ypos);

		}

	}

	public boolean imageClicked(AniImage which) {
		int timenow = Vm.getTimeStamp();

		// avoid double clicks
		if (timenow < 100 + lastTime) {
			return false;
		}
		lastTime = timenow;
		for (int i = 0; i < buttons.size(); i++) {
			MovingMapControlItem item = (MovingMapControlItem) buttons.get(i);
			AniImage ani = item.getImage();
			if (which == ani) {
				String command = item.getActionCommand();
				if (helpIsVisible && item.getHelp() != null) {
					// iconHelp.setText(item.getHelp());
					movingMap.repaintNow();
				} else if ("changeStateOfRole".equals(command)) {
					boolean val =changeRoleState(item.getRoleToChange());
					if (val) {
						changeRoleState("menu", Boolean.FALSE);
					}
					setStateOfIcons();
					movingMap.repaintNow();
					return val;
				} else if (movingMap.handleCommand(command)) {
					changeRoleState("menu", Boolean.FALSE);
				}				
				return true;
			}
		}
		changeRoleState("menu", Boolean.FALSE);
		return false;
	}

	

	public boolean handleCommand(String actionCommand) {

		if (CLOSE.equals(actionCommand)) {
			roles.put("menu", Boolean.FALSE);
		}

		return false;
	}

	// Methods for the eventlistener

	/**
	 * Method to react to user.
	 */
	public boolean handleImageClickedEvent(AniImage which) {
		if (which == null)
			return false;

		// if menu is oben but klickt to an other place, close menu
		boolean imageClicked = imageClicked(which);

		// boolean menuIsVisible= getStateofRole("menu");;
		// if (!imageClicked && menuIsVisible) {
		// roles.put("menu",Boolean.FALSE);
		// }
		return imageClicked;
	}


	public boolean imageBeginDragged(AniImage which, Point pos) {

		imageClicked(which);
		// no moving of images is needed here. Return false to say the moving event is cleared.
		return false;
	}

	
	public static class Role{
		Boolean state = Boolean.FALSE;
		String rolesToDisable[]=null;
		
		public void setRolesToDisable(String[] rolesToDisable) {
			this.rolesToDisable = rolesToDisable;
		}
		
		public void setState(Boolean state) {
			this.state = state;
		}
		
		public Boolean getState() {
			return state;
		}
		
		public String[] getRolesToDisable() {
			return rolesToDisable;
		}
	}
}