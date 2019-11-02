package views;

import static models.GameMap.getGameMap;

/**
 * View for cli
 */
public class ConsoleView {

  /**
   * Displays text to the console
   *
   * @param text string to display
   */
  public static void display(String text, boolean writeLog) {
    if(writeLog) getGameMap().setPhaseLog(String.format("-> " + text + "\n"), false);
    System.out.println("-> " + text);
  }

}