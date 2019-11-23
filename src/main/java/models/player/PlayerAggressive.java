package models.player;

import controllers.BattleController;
import controllers.GameController;
import models.Card;
import models.Country;
import models.GameMap;
import views.CardExchangeView;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;
import static views.ConsoleView.display;

public class PlayerAggressive extends Observable implements PlayerStrategy {
  /** Maintains the number of sets traded in game */
  private static int numberOfTradedSet = 0;
  /** Number of armies traded in for each set */
  private static int armiesTradedForSet = 0;
  /** This instance variable holds the name of the player. */
  private String playerName;
  /** Stores the number of armies a player has. */
  private int numberOfArmies;
  /** Stores the cards currently held by the player. */
  private ArrayList<Card> cardsInHand = new ArrayList<>();
  /** How many turns have elapsed */
  private int turnCount = 0;

  public PlayerAggressive(String name) {
    this.setPlayerName(name);
  }

  public void addObserver(CardExchangeView object) {
    super.addObserver(object);
  }

  private Map.Entry<String, Country> getStrongestCountryAttackReinforce(
      GameMap gameMap, String op) {
    ArrayList<Country> countriesOwnedByPlayer =
        Player.getCountriesByOwnership(this.getPlayerName(), gameMap);

    if (op.equals("attack")) {
      countriesOwnedByPlayer =
          countriesOwnedByPlayer.stream()
              .filter(c -> c.getNumberOfArmies() > 1)
              .sorted(Comparator.comparingInt(Country::getNumberOfArmies).reversed())
              .collect(toCollection(ArrayList::new));
    } else {
      countriesOwnedByPlayer =
          countriesOwnedByPlayer.stream()
              .sorted(Comparator.comparingInt(Country::getNumberOfArmies).reversed())
              .collect(toCollection(ArrayList::new));
    }

    Optional<Country> resEntry =
        countriesOwnedByPlayer.stream()
            .filter(
                country ->
                    !gameMap.getBorders().get(country.getName()).stream()
                        .map(gameMap.getCountries()::get)
                        .allMatch(neighbor -> neighbor.getOwnerName().equals(this.playerName)))
            .findFirst();

    if (resEntry.isPresent())
      return new AbstractMap.SimpleEntry<>(resEntry.get().getName(), resEntry.get());
    else return null;
  }

  private Map.Entry<String, Country> getStrongestCountryFortify(GameMap gameMap) {
    ArrayList<Country> countriesOwnedByPlayer =
        Player.getCountriesByOwnership(this.getPlayerName(), gameMap);
    countriesOwnedByPlayer =
        countriesOwnedByPlayer.stream()
            .sorted(Comparator.comparingInt(Country::getNumberOfArmies).reversed())
            .collect(toCollection(ArrayList::new));
    Map.Entry<String, Country> resEntry = null;
    for (Country c : countriesOwnedByPlayer) {
      boolean noOwnedNeighbor =
          gameMap.getBorders().get(c.getName()).stream()
              .noneMatch(
                  neighbor ->
                      gameMap.getCountries().get(neighbor).getOwnerName().equals(this.playerName));
      if (!noOwnedNeighbor) {
        resEntry = new AbstractMap.SimpleEntry<String, Country>(c.getName(), c);
        break;
      }
    }
    return resEntry;
  }

  private void attackUtil(GameMap gameMap, Map.Entry<String, Country> countryWithMaxArmies) {
    if (countryWithMaxArmies == null) {
      display("No strongest country left to attack", true);
      GameController.assignedCard = false;
      GameController.changeToNextPhase(gameMap);
      return;
    }
    String countryToAttack =
        gameMap.getBorders().get(countryWithMaxArmies.getKey()).stream()
            .filter(
                neighbor ->
                    !gameMap.getCountries().get(neighbor).getOwnerName().equals(this.playerName))
            .reduce(
                null,
                (minArmiesNeighbor, country) -> {
                  if (minArmiesNeighbor == null) return country;
                  Country minArmiesNeighborCountry = gameMap.getCountries().get(minArmiesNeighbor);
                  Country currentCountry = gameMap.getCountries().get(country);
                  if (currentCountry.getNumberOfArmies()
                      < minArmiesNeighborCountry.getNumberOfArmies()) return country;
                  return minArmiesNeighbor;
                });

    String command =
        String.format("attack %s %s -allout", countryWithMaxArmies.getKey(), countryToAttack);
    if (GameController.validateAttack(gameMap, command)) {
      BattleController battleController = new BattleController(gameMap, command);
      battleController.setNoInputEnabled(true);
      battleController.startBattle();
    }
  }

  @Override
  public boolean attack(GameMap gameMap, String blankCommand) {
    // recursively with strongest valid country until he can't attack
    while (gameMap.getCurrentContext().name().contains("ATTACK")) {
      Map.Entry<String, Country> countryWithMaxArmies =
          getStrongestCountryAttackReinforce(gameMap, "attack");
      attackUtil(gameMap, countryWithMaxArmies);
    }
    return true;
  }

  @Override
  public boolean reinforce(GameMap gameMap, String countryToPlace, int armiesToPlace) {
    // move all armies to strongest country
    // check if cardExchange is possible
    // perform cardExchange and then reinforce
    PlayerAggressive currPlayer = (PlayerAggressive) gameMap.getCurrentPlayer().getStrategy();
    if (cardsInHand.size() >= 5) {
      currPlayer.exchangeCardsForArmies(Player.getCardExchangeIndices(this.getCardsInHand()));
    }
    int armiesToReinforce = currPlayer.getNumberOfArmies();
    String countryToReinforce = getStrongestCountryAttackReinforce(gameMap, "reinforce").getKey();
    gameMap.placeArmy(countryToReinforce, armiesToReinforce);
    display(
        String.format(
            "%s reinforced %s with %d armies", currPlayer, countryToReinforce, armiesToReinforce),
        true);
    return true;
  }

  @Override
  public boolean fortify(GameMap gameMap, String fromCountry, String toCountry, int armyToMove) {
    // get the country with max number of armies
    // check if fortify possible
    // aggregate armies in this country FROM the next strongest country
    boolean result = false;

    String currPlayerName = gameMap.getCurrentPlayer().getStrategy().getPlayerName();
    Map.Entry<String, Country> tCountryEntry = getStrongestCountryFortify(gameMap);
    Country tCountry = tCountryEntry == null ? null : tCountryEntry.getValue();
    Country fCountry;
    String fCountryStr = null;
    String tCountryStr = null;
    if (tCountry != null) {
      fCountry = findStrongestAlongDFSPath(gameMap, currPlayerName, tCountry.getName());
      if (fCountry == null) {
        display(String.format("%s chose not to fortify", currPlayerName), true);
        return true;
      }
      fCountryStr = fCountry.getName();
      tCountryStr = tCountry.getName();
    }

    if (fCountryStr != null && tCountryStr != null) {
      int fCountryArmies = gameMap.getCountries().get(fCountryStr).getNumberOfArmies();
      int armiesToMove = 0;

      if (fCountryArmies < 2) {
        display(String.format("%s chose not to fortify", currPlayerName), true);
        return true;
      } else {
        armiesToMove = gameMap.getCountries().get(fCountryStr).getNumberOfArmies() - 1;
        boolean isArmyRemoved = gameMap.getCountries().get(fCountryStr).removeArmies(armiesToMove);
        if (isArmyRemoved) {
          gameMap.getCountries().get(tCountryStr).addArmies(armiesToMove);
          result = true;
        }
      }

      if (result) {
        this.turnCount++;
        display(
            String.format(
                "%s Fortified %s with %d army(s) from %s",
                currPlayerName, tCountryStr, armiesToMove, fCountryStr),
            true);
      }
    } else {
      display(String.format("%s chose not to fortify", currPlayerName), true);
      result = true;
    }
    return result;
  }

  private Country findStrongestAlongDFSPath(
      GameMap gameMap, String currPlayerName, String maxCountry) {
    ArrayList<Country> countriesOwnedByPlayer =
        Player.getCountriesByOwnership(currPlayerName, gameMap);
    ArrayList<String> countriesOwnedByPlayerStrings =
        countriesOwnedByPlayer.stream().map(Country::getName).collect(toCollection(ArrayList::new));

    Map<String, Set<String>> copyMapBorders = new HashMap<>(gameMap.getBorders());
    Map<String, Set<String>> filteredGameMap =
        copyMapBorders.entrySet().stream()
            .filter(entry -> countriesOwnedByPlayerStrings.contains(entry.getKey()))
            .map(
                entry ->
                    new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        entry.getValue().stream()
                            .filter(countriesOwnedByPlayerStrings::contains)
                            .collect(toSet())))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    ArrayList<String> DFSNeighbors =
        new ArrayList<>(DFSUtil(gameMap, filteredGameMap, new HashSet<>(), maxCountry));
    DFSNeighbors.remove(maxCountry);
    String fromFortifyCountry =
        DFSNeighbors.isEmpty() ? "none" : getFromFortifyCountry(gameMap, DFSNeighbors);

    if (!fromFortifyCountry.equals("none")) return gameMap.getCountries().get(fromFortifyCountry);
    return null;
  }

  private Set<String> DFSUtil(
      GameMap gameMap,
      Map<String, Set<String>> filteredGameMap,
      Set<String> visited,
      String start) {
    visited.add(start);
    for (String neighbor : filteredGameMap.get(start)) {
      if (!visited.contains(neighbor)) {
        DFSUtil(gameMap, filteredGameMap, visited, neighbor);
      }
    }
    return visited;
  }

  private String getFromFortifyCountry(GameMap gameMap, ArrayList<String> DFSNeighbors) {
    ArrayList<Country> sortedReverse =
        DFSNeighbors.stream()
            .map(neighbor -> gameMap.getCountries().get(neighbor))
            .sorted(Comparator.comparingInt(Country::getNumberOfArmies).reversed())
            .collect(Collectors.toCollection(ArrayList::new));
    return sortedReverse.get(0).getName();
  }
  /**
   * This method gives armies to the player
   *
   * @return int with the number of armies.
   */
  public int giveArmies() {
    if (numberOfTradedSet == 1) {
      armiesTradedForSet += 4;
    } else if (numberOfTradedSet < 6) {
      armiesTradedForSet += 2;
    } else if (numberOfTradedSet == 6) {
      armiesTradedForSet += 3;
    } else {
      armiesTradedForSet += 5;
    }

    return armiesTradedForSet;
  }

  /**
   * This method returns the name of the player.
   *
   * @return playerName the name of the player.
   */
  public String getPlayerName() {
    return playerName;
  }

  /**
   * This method sets the name of the player.
   *
   * @param playername the name of the player.
   */
  public void setPlayerName(String playername) {
    this.playerName = playername;
  }

  /**
   * Getter for number of armies the player owns.
   *
   * @return int with number of armies
   */
  public int getNumberOfArmies() {
    return this.numberOfArmies;
  }

  /**
   * Setter for number of armies the player owns.
   *
   * @param numberOfArmies int with the number of armies.
   */
  public void setNumberOfArmies(int numberOfArmies) {
    this.numberOfArmies = numberOfArmies;
  }

  /** This is an override for pretty printing the name. */
  @Override
  public String toString() {
    return String.format("%s", this.playerName);
  }

  /**
   * Returns the player's hand.
   *
   * @return List with the Cards
   */
  public ArrayList<Card> getCardsInHand() {
    return cardsInHand;
  }

  /**
   * Sets the cards in the player's hand.
   *
   * @param cardsInHand A collection of Card objects.
   */
  public void setCardsInHand(ArrayList<Card> cardsInHand) {
    this.cardsInHand = cardsInHand;
    setChanged();
    notifyObservers();
  }

  /**
   * Adds a card to this player's hand.
   *
   * @param card The Card object to be added.
   */
  public void addCard(Card card) {
    this.cardsInHand.add(card);
    setChanged();
    notifyObservers();
  }

  /**
   * This method removes armies from the player
   *
   * @param count armies to subtract from the player
   */
  public void subtractArmies(int count) {
    this.numberOfArmies -= count;
  }

  /**
   * Exchange the card for armies.
   *
   * @param indices the positions of the cards in the list.
   */
  public void exchangeCardsForArmies(int[] indices) {
    Set<String> cardSet = new HashSet<>();
    for (int index : indices) {
      if (index >= 0 && index < cardsInHand.size()) {
        cardSet.add(cardsInHand.get(index).getCardValue());
      } else {
        display("One OR more of your card indices are INCORRECT", false);
        return;
      }
    }
    if (cardSet.size() == 1 || cardSet.size() == 3) {
      numberOfTradedSet++;
      int armiesAcquired = giveArmies();
      numberOfArmies += armiesAcquired;

      ArrayList<Card> cardsToAddToDeck = new ArrayList<>();
      for (int index : indices) {
        cardsToAddToDeck.add(cardsInHand.get(index));
      }

      ArrayList<Integer> listIndices =
          Arrays.stream(indices)
              .boxed()
              .sorted(Comparator.reverseOrder())
              .collect(toCollection(ArrayList::new));

      ArrayList<Card> resultCardsInHand = new ArrayList<>();
      for (int i = 0; i < cardsInHand.size(); i++) {
        if (!listIndices.contains(i)) {
          resultCardsInHand.add(cardsInHand.get(i));
        }
      }
      setCardsInHand(resultCardsInHand);

      Collections.shuffle(cardsToAddToDeck);
      GameMap.getGameMap()
          .getDeck()
          .addAll(
              cardsToAddToDeck); // add the exchanged cards to deck after removing from player hand
      display("EXCHANGING CARDS!", true);
      display("Acquired " + armiesAcquired + " through card exchange", false);
    } else {
      display(
          "The set provided is not valid. Valid set: 3 cards of same type or 3 cards of different type",
          true);
    }
  }
}
