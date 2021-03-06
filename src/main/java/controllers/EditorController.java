package controllers;

import models.Context;
import models.GameMap;
import utils.MapAdaptor;
import utils.MapValidator;

import java.util.Arrays;

import static views.ConsoleView.display;

/**
 * Controller for Editor Context
 *
 * @version 1.0
 * @author Sabari
 */
public class EditorController {

  /**
   * Processes the edit continent command.
   *
   * @param gameMap The GameMap object to modify.
   * @param command The command received from cli
   * @return A boolean with success or failure for the command.
   */
  public static boolean processEditContinentCommand(GameMap gameMap, String command) {
    boolean result = false;
    String[] commandSplit = command.split(" -");

    for (String subCommand : Arrays.copyOfRange(commandSplit, 1, commandSplit.length)) {
      String[] subCommandSplit = subCommand.split(" ");
      String commandType = subCommandSplit[0];
      String continentName = subCommandSplit[1];
      if (commandType.equals("add")) {
        int continentControlValue = Integer.parseInt(subCommandSplit[2]);
        result = gameMap.addContinent(continentName, continentControlValue);
        if (result) {
          display(String.format("Added continent: %s", continentName), false);
        }
      } else {
        result = gameMap.removeContinent(continentName);
        if (result) {
          display(String.format("Removed continent: %s", continentName), false);
        } else {
          display(String.format("The continent %s does not exist", continentName), false);
        }
      }
    }
    return result;
  }

  /**
   * Processes the edit country command.
   *
   * @param gameMap The GameMap object to modify
   * @param command The command received from cli
   * @return A boolean result for success or failure.
   */
  public static boolean processEditCountryCommand(GameMap gameMap, String command) {
    boolean result = false;
    String[] commandSplit = command.split(" -");
    for (String subCommand : Arrays.copyOfRange(commandSplit, 1, commandSplit.length)) {
      String[] subCommandSplit = subCommand.split(" ");
      String commandType = subCommandSplit[0];
      String countryName = subCommandSplit[1];
      if (commandType.equals("add")) {
        String continentName = subCommandSplit[2];
        result = gameMap.addCountry(countryName, continentName);
        if (result) {
          display(String.format("Added country: %s to %s", countryName, continentName), false);
        } else {
          display(String.format("The continent %s does not exist", continentName), false);
        }
      } else {
        result = gameMap.removeCountry(countryName);
        if (result) {
          display(String.format("Removed country: %s", countryName), false);
        } else {
          display(String.format("The country %s does not exist", countryName), false);
        }
      }
    }
    return result;
  }

  /**
   * Processes the edit neighbour command.
   *
   * @param gameMap The GameMap object to modify.
   * @param command The command options.
   * @return A boolean with success or failure.
   */
  public static boolean processEditNeighborCommand(GameMap gameMap, String command) {
    boolean result = false;
    String[] commandSplit = command.split(" -");
    for (String subCommand : Arrays.copyOfRange(commandSplit, 1, commandSplit.length)) {
      String[] subCommandSplit = subCommand.split(" ");
      String commandType = subCommandSplit[0];
      String country1 = subCommandSplit[1];
      String country2 = subCommandSplit[2];
      if (country1.equals(country2)) {
        result = false;
        display(String.format("The countries %s and %s are the same", country1, country2), false);
        break;
      }
      if (commandType.equals("add")) {
        result = gameMap.addBorder(country1, country2);
        if (result) {
          display(String.format("Added border: %s - %s", country1, country2), false);
        } else {
          display(
              String.format("One of the countries %s, %s does not exist", country2, country1),
              false);
        }
      } else if (commandType.equals("remove")) {
        result = gameMap.removeBorder(country1, country2);
        if (result) {
          display("Removed border: " + country1 + " - " + country2, false);
        } else {
          display(
              String.format("One of the countries %s, %s does not exist", country2, country1),
              false);
        }
      }
    }
    return result;
  }

  /**
   * Processes validatemap from the cli Validates a map's connectivity and its subgraph connectivity
   *
   * @param gameMap contains game state
   * @param command cli command from the user
   * @return boolean to indicate map validity
   */
  public static boolean processValidateMapCommand(GameMap gameMap, String command) {
    boolean result = MapValidator.validateMap(gameMap);
    if (result) {
      display("Game map is valid", false);
    } else {
      display("Game map is invalid", false);
    }
    return result;
  }

  /**
   * Processes savemap from the cli Serializes the game state and saves it to a filelocation
   *
   * @param gameMap contains game state
   * @param command cli command from the user containing filelocation
   * @return boolean to indicate the status
   */
  public static boolean processSaveMapCommand(GameMap gameMap, String command) {
    String fileLocation = command.split(" ", 2)[1];
    boolean result = false;
    boolean isMapValid = processValidateMapCommand(gameMap, command);
    if (isMapValid) {
      MapAdaptor mapAdaptor = new MapAdaptor();
      try {
        result = mapAdaptor.autoSaveMap(gameMap, fileLocation);
      } catch (Exception e) {
        display("Unable to save map " + e.getMessage(), true);
      }
      if (result) {
        display("Game map saved to " + fileLocation, false);
        gameMap.setCurrentContext(Context.MAIN_MENU);
      } else {
        display(fileLocation + " is invalid", false);
      }
    } else {
      display("Can't save an invalid map", false);
    }
    return result;
  }

  /**
   * Show map connectivity, continents, countries
   *
   * @param gameMap contains game state
   * @param command command from the cli
   * @return true indicating status
   */
  public static boolean processShowMapCommand(GameMap gameMap, String command) {
    display(gameMap.toString(), false);
    return true;
  }
}
