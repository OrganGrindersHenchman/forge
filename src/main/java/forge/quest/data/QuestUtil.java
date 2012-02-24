/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.quest.data;

import java.util.List;

import forge.AllZone;
import forge.Card;
import forge.CardList;
import forge.CardUtil;
import forge.Constant;
import forge.Player;
import forge.card.CardRarity;
import forge.item.CardPrinted;
import forge.quest.BoosterUtils;

/**
 * <p>
 * QuestUtil class.
 * </p>
 * MODEL - Static utility methods to help with minor tasks around Quest.
 * 
 * @author Forge
 * @version $Id$
 */
public class QuestUtil {
    /**
     * <p>
     * getComputerStartingCards.
     * </p>
     * 
     * @param qd
     *            a {@link forge.quest.data.QuestData} object.
     * @return a {@link forge.CardList} object.
     */
    public static CardList getComputerStartingCards(final QuestData qd) {
        return new CardList();
    }

    /**
     * <p>
     * getComputerStartingCards.
     * </p>
     * Returns new card instances of extra AI cards in play at start of event.
     * 
     * @param qd
     *            a {@link forge.quest.data.QuestData} object.
     * @param qe
     *            a {@link forge.quest.data.QuestEvent} object.
     * @return a {@link forge.CardList} object.
     */
    public static CardList getComputerStartingCards(final QuestData qd, final QuestEvent qe) {
        final CardList list = new CardList();

        if (qe.getEventType().equals("challenge")) {
            final List<String> extras = ((QuestChallenge) qe).getAIExtraCards();

            for (final String s : extras) {
                list.add(QuestUtil.readExtraCard(s, AllZone.getComputerPlayer()));
            }
        }

        return list;
    }

    /**
     * <p>
     * getHumanStartingCards.
     * </p>
     * Returns list of current plant/pet configuration only.
     * 
     * @param qd
     *            a {@link forge.quest.data.QuestData} object.
     * @return a {@link forge.CardList} object.
     */
    public static CardList getHumanStartingCards(final QuestData qd) {
        final CardList list = new CardList();

        if (qd.getPetManager().shouldPetBeUsed()) {
            list.add(qd.getPetManager().getSelectedPet().getPetCard());
        }

        if (qd.getPetManager().shouldPlantBeUsed()) {
            list.add(qd.getPetManager().getPlant().getPetCard());
        }

        return list;
    }

    /**
     * <p>
     * getHumanStartingCards.
     * </p>
     * Returns new card instances of extra human cards, including current
     * plant/pet configuration, and cards in play at start of quest.
     * 
     * @param qd
     *            a {@link forge.quest.data.QuestData} object.
     * @param qe
     *            a {@link forge.quest.data.QuestEvent} object.
     * @return a {@link forge.CardList} object.
     */
    public static CardList getHumanStartingCards(final QuestData qd, final QuestEvent qe) {
        final CardList list = QuestUtil.getHumanStartingCards(qd);

        if (qe.getEventType().equals("challenge")) {
            final List<String> extras = ((QuestChallenge) qe).getHumanExtraCards();

            for (final String s : extras) {
                list.add(QuestUtil.readExtraCard(s, AllZone.getHumanPlayer()));
            }
        }

        return list;
    }

    /**
     * <p>
     * createToken.
     * </p>
     * Creates a card instance for token defined by property string.
     * 
     * @param s
     *            Properties string of token
     *            (TOKEN;W;1;1;sheep;type;type;type...)
     * @return token Card
     */
    public static Card createToken(final String s) {
        final String[] properties = s.split(";");
        final Card c = new Card();
        c.setToken(true);

        // c.setManaCost(properties[1]);
        c.addColor(properties[1]);
        c.setBaseAttack(Integer.parseInt(properties[2]));
        c.setBaseDefense(Integer.parseInt(properties[3]));
        c.setName(properties[4]);

        c.setImageName(properties[1] + " " + properties[2] + " " + properties[3] + " " + properties[4]);

        int x = 5;
        while (x != properties.length) {
            c.addType(properties[x++]);
        }

        return c;
    }

    /**
     * <p>
     * generateCardRewardList.
     * </p>
     * Takes a reward list string, parses, and returns list of cards rewarded.
     * 
     * @param s
     *            Properties string of reward (97 multicolor rares)
     * @return CardList
     */
    public static List<CardPrinted> generateCardRewardList(final String s) {
        final String[] temp = s.split(" ");

        final int qty = Integer.parseInt(temp[0]);
        // Determine rarity
        CardRarity rar = CardRarity.Uncommon;
        if (temp[2].equalsIgnoreCase("rare") || temp[2].equalsIgnoreCase("rares")) {
            rar = CardRarity.Rare;
        }

        // Determine color ("random" defaults to null color)
        String col = null;
        if (temp[1].equalsIgnoreCase("black")) {
            col = Constant.Color.BLACK;
        } else if (temp[1].equalsIgnoreCase("blue")) {
            col = Constant.Color.BLUE;
        } else if (temp[1].equalsIgnoreCase("colorless")) {
            col = Constant.Color.COLORLESS;
        } else if (temp[1].equalsIgnoreCase("green")) {
            col = Constant.Color.GREEN;
        } else if (temp[1].equalsIgnoreCase("multicolor")) {
            col = "Multicolor"; // Note: No constant color for this??
        } else if (temp[1].equalsIgnoreCase("red")) {
            col = Constant.Color.RED;
        } else if (temp[1].equalsIgnoreCase("white")) {
            col = Constant.Color.WHITE;
        }

        return BoosterUtils.generateCards(qty, rar, col);
    }

    /**
     * <p>
     * readExtraCard.
     * </p>
     * Creates single card for a string read from unique event properties.
     * 
     * @param name
     *            the name
     * @param owner
     *            the owner
     * @return the card
     */
    public static Card readExtraCard(final String name, final Player owner) {
        // Token card creation
        Card tempcard;
        if (name.startsWith("TOKEN")) {
            tempcard = QuestUtil.createToken(name);
            tempcard.addController(owner);
            tempcard.setOwner(owner);
        }
        // Standard card creation
        else {
            tempcard = AllZone.getCardFactory().getCard(name, owner);
            tempcard.setCurSetCode(tempcard.getMostRecentSet());
            tempcard.setImageFilename(CardUtil.buildFilename(tempcard));
        }
        return tempcard;
    }

} // QuestUtil
