/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.campaign.mission.contract;

import static mekhq.campaign.mission.RandomFactionCamouflage.pickRandomCamouflage;
import static mekhq.campaign.mission.enums.ContractCommandRights.INDEPENDENT;
import static mekhq.campaign.personnel.ranks.Rank.RO_MIN;
import static mekhq.campaign.universe.Faction.INDEPENDENT_FACTION_CODE;
import static mekhq.utilities.MHQInternationalization.getFormattedTextAt;
import static mekhq.utilities.MHQInternationalization.getTextAt;

import java.io.PrintWriter;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import jakarta.annotation.Nullable;
import megamek.Version;
import megamek.common.enums.Gender;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.AbstractMissionTransition;
import mekhq.campaign.mission.Mission;
import mekhq.campaign.mission.enums.ContractCommandRights;
import mekhq.campaign.mission.enums.MissionStatus;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.enums.PersonnelRole;
import mekhq.campaign.personnel.ranks.AutoAssignRankForCompanyGenerator;
import mekhq.campaign.unit.Unit;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.utilities.MHQXMLUtility;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class AbstractContract {
    private static final MMLogger LOGGER = MMLogger.create(AbstractContract.class);
    private static final String RESOURCE_BUNDLE = "mekhq.resources.AbstractContract";

    public final static int OH_NONE = 0;
    public final static int OH_HALF = 1;
    public final static int OH_FULL = 2;
    public final static int OH_NUM = 3;

    private final static int MRBC_FEE_PERCENTAGE = 5;
    private final static int DEFAULT_SHARES_PERCENT = 30;
    public static final int UNKNOWN_DIFFICULTY = -99;

    private String name;
    private int id = -1;
    private MissionStatus status = MissionStatus.ACTIVE;
    private String description;

    private String employerCode = INDEPENDENT_FACTION_CODE;
    private String employerName = getTextAt(RESOURCE_BUNDLE, "AbstractContract.belligerentName.default");
    private Person employerLiaison;

    private int contractDifficulty = 5;

    private double paymentMultiplier = 1.0;
    private ContractCommandRights commandRights = INDEPENDENT;
    private int overheadCompensation = OH_NONE;
    private int straightSupport;
    private int battleLossCompensation;
    private int salvagePercent;
    private int transportCompensation;

    // need to keep track of total value salvaged for salvage rights
    private boolean salvageExchange;
    private Money salvagedByUnit = Money.zero();
    private Money salvagedByEmployer = Money.zero();

    private boolean paidMRBCFee = true;
    private int mrbcFeePercent = MRBC_FEE_PERCENTAGE;
    private int sharesPercent = DEFAULT_SHARES_PERCENT;
    private int advancePercent;
    private int signingBonus;

    private int contractNegotiationCommandRoll;
    private int contractNegotiationSalvageRoll;
    private int contractNegotiationSupportRoll;
    private int contractNegotiationTransportRoll;

    private final List<AbstractMission> missions = new ArrayList<>();

    protected final Map<String, BiConsumer<String, Campaign>> fieldSetters = new HashMap<>();
    protected final Map<String, AbstractContract.NodeSetter> nodeSetters = new HashMap<>();

    AbstractContract() {}

    /**
     * Calculations to be performed once the contract has been accepted.
     */
    public void acceptContract(Campaign campaign) {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the name of this object as an HTML hyperlink.
     *
     * <p>The hyperlink is formatted with a "MISSION:" protocol prefix followed by the object's ID. This allows UI
     * components that support HTML to render the name as a clickable link, which can be used to navigate to or focus on
     * this specific object when clicked.</p>
     *
     * @return An HTML formatted string containing the object's name as a hyperlink with its ID
     *
     * @author Illiani
     * @since 0.50.05
     */
    public String getHyperlinkedName() {
        return String.format("<a href='MISSION:%s'>%s</a>", getId(), getName());
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public boolean isActiveOn(LocalDate date) {
        return isActiveOn(date, false);
    }

    public boolean isActiveOn(LocalDate date, boolean excludeEndDateCheck) {
        return getStatus().isActive();
    }

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public String getEmployerName(int year) {
        return getEmployerFaction().getFullName(year);
    }

    public void updateEmployer(String code, int year) {
        this.setEmployerCode(code);
        setEmployerName(getEmployerName(year));
        setAllyCamouflage(year);
    }

    private void setAllyCamouflage(int year) {
        for (AbstractMission mission : getMissions()) {
            mission.setAllyCamouflage(pickRandomCamouflage(year, getEmployerCode()));
        }
    }

    public @Nullable Person getEmployerLiaison() {
        return employerLiaison;
    }

    public Faction getEmployerFaction() {
        return Factions.getInstance().getFaction(getEmployerCode());
    }

    public void setEmployerLiaison(@Nullable Person employerLiaison) {
        this.employerLiaison = employerLiaison;
    }

    public void createEmployerLiaison(Campaign campaign) {
        setEmployerLiaison(campaign.newPerson(PersonnelRole.MILITARY_LIAISON, getEmployerCode(), Gender.RANDOMIZE));

        AutoAssignRankForCompanyGenerator.assignAscendingRank(getEmployerLiaison(), RO_MIN);
    }

    public @Nullable String getEmployerCode() {
        return employerCode;
    }

    public void setEmployerCode(String employerCode) {
        this.employerCode = employerCode;
    }

    public int getContractDifficulty() {
        return contractDifficulty;
    }

    public void setContractDifficulty(int contractDifficulty) {
        this.contractDifficulty = contractDifficulty;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getContractNegotiationTransportRoll() {
        return contractNegotiationTransportRoll;
    }

    public void setContractNegotiationTransportRoll(int contractNegotiationTransportRoll) {
        this.contractNegotiationTransportRoll = contractNegotiationTransportRoll;
    }

    public int getContractNegotiationSupportRoll() {
        return contractNegotiationSupportRoll;
    }

    public void setContractNegotiationSupportRoll(int contractNegotiationSupportRoll) {
        this.contractNegotiationSupportRoll = contractNegotiationSupportRoll;
    }

    public int getContractNegotiationSalvageRoll() {
        return contractNegotiationSalvageRoll;
    }

    public void setContractNegotiationSalvageRoll(int contractNegotiationSalvageRoll) {
        this.contractNegotiationSalvageRoll = contractNegotiationSalvageRoll;
    }

    public int getContractNegotiationCommandRoll() {
        return contractNegotiationCommandRoll;
    }

    public void setContractNegotiationCommandRoll(int contractNegotiationCommandRoll) {
        this.contractNegotiationCommandRoll = contractNegotiationCommandRoll;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSalvagePercent() {
        return salvagePercent;
    }

    public void setSalvagePercent(int salvagePercent) {
        this.salvagePercent = salvagePercent;
    }

    public String getSalvagePercentString() {
        return getSalvagePercent() + "%";
    }

    public boolean canSalvage() {
        return getSalvagePercent() > 0;
    }

    public double getPaymentMultiplier() {
        return paymentMultiplier;
    }

    public void setPaymentMultiplier(double paymentMultiplier) {
        this.paymentMultiplier = paymentMultiplier;
    }

    public ContractCommandRights getCommandRights() {
        return commandRights;
    }

    public void setCommandRights(ContractCommandRights commandRights) {
        this.commandRights = commandRights;
    }

    public int getOverheadCompensation() {
        return overheadCompensation;
    }

    public void setOverheadCompensation(int overheadCompensation) {
        this.overheadCompensation = overheadCompensation;
    }

    public int getStraightSupport() {
        return straightSupport;
    }

    public void setStraightSupport(int straightSupport) {
        this.straightSupport = Math.clamp(straightSupport, 0, 100);
    }

    public String getStraightSupportString() {
        return getStraightSupport() + "%";
    }

    public int getBattleLossCompensation() {
        return battleLossCompensation;
    }

    public void setBattleLossCompensation(int battleLossCompensation) {
        this.battleLossCompensation = Math.clamp(battleLossCompensation, 0, 100);
    }

    public String getBattleLossCompString() {
        return getBattleLossCompensation() + "%";
    }

    public boolean isSalvageExchange() {
        return salvageExchange;
    }

    public void setSalvageExchange(boolean salvageExchange) {
        this.salvageExchange = salvageExchange;
    }

    public int getTransportCompensation() {
        return transportCompensation;
    }

    public void setTransportCompensation(int transportCompensation) {
        this.transportCompensation = transportCompensation;
    }

    public String getTransportCompString() {
        return getTransportCompensation() + "%";
    }

    public static String getOverheadCompensationName(int i) {
        return switch (i) {
            case OH_NONE -> "None";
            case OH_HALF -> "Half";
            case OH_FULL -> "Full";
            default -> "?";
        };
    }

    public Money getSalvagedByUnit() {
        return salvagedByUnit;
    }

    public void setSalvagedByUnit(Money salvagedByUnit) {
        this.salvagedByUnit = salvagedByUnit;
    }

    public void addSalvageByUnit(Money money) {
        salvagedByUnit = salvagedByUnit.plus(money);
    }

    public void subtractSalvageByUnit(Money money) {
        salvagedByUnit = salvagedByUnit.minus(money);
    }

    public Money getSalvagedByEmployer() {
        return salvagedByEmployer;
    }

    public void setSalvagedByEmployer(Money salvagedByEmployer) {
        this.salvagedByEmployer = salvagedByEmployer;
    }

    public void addSalvageByEmployer(Money money) {
        salvagedByEmployer = salvagedByEmployer.plus(money);
    }

    /**
     * Computes the player's share of the total salvage value as an integer percentage, using
     * {@link RoundingMode#CEILING} (i.e. any fractional percentage rounds up to the next whole percent).
     *
     * <p>Rounding up is intentional from a gameplay standpoint: the percentage is compared against the contract's
     * salvage cap, and a true value of e.g. 50.001% against a 50% cap is a breach and must be surfaced as such. It also
     * fixes the truncation artifacts that previously could cause the displayed value to shift by a full percentage
     * point after a small change to the salvage assignment (see issue #5683).</p>
     *
     * @param playerShare   the salvage value assigned to the player (mercs)
     * @param employerShare the salvage value assigned to the employer
     *
     * @return integer percentage in the range {@code [0, 100]}, or {@code 0} if there is no salvage to split
     */
    public static int calculateSalvagePercentage(Money playerShare, Money employerShare) {
        Money total = playerShare.plus(employerShare);
        if (!total.isPositive()) {
            return 0;
        }
        return playerShare.multipliedBy(100)
                     .getAmount()
                     .divide(total.getAmount(), 0, RoundingMode.CEILING)
                     .intValue();
    }


    /**
     * Convenience overload that computes the current salvage percentage from the values stored on this contract.
     *
     * @return integer percentage in the range {@code [0, 100]}, or {@code 0} if there is no salvage to split
     */
    public int getCurrentSalvagePct() {
        return calculateSalvagePercentage(getSalvagedByUnit(), getSalvagedByEmployer());
    }

    public boolean isPaidMRBCFee() {
        return paidMRBCFee;
    }

    public void setPaidMRBCFee(boolean paidMRBCFee) {
        this.paidMRBCFee = paidMRBCFee;
    }

    public int getMRBCFeePercentage() {
        return mrbcFeePercent;
    }

    public void setMRBCFeePercentage(int mrbcFeePercent) {
        this.mrbcFeePercent = mrbcFeePercent;
    }

    /**
     * Retrieves the percentage of shares for this contract. This currently returns a default value of 30.
     *
     * @return the percentage of shares
     */
    public int getSharesPercent() {
        return sharesPercent;
    }

    public void setSharesPercent(int sharesPercent) {
        this.sharesPercent = sharesPercent;
    }

    public int getAdvancePercent() {
        return advancePercent;
    }

    public void setAdvancePercent(int advancePercent) {
        this.advancePercent = advancePercent;
    }

    public int getSigningBonus() {
        return signingBonus;
    }

    public void setSigningBonus(int signingBonus) {
        this.signingBonus = signingBonus;
    }

    /**
     * Only do this at the time the contract is set up, otherwise amounts may change after the ink is signed, which is a
     * no-no.
     *
     * @param campaign current campaign
     */
    public void calculateContract(Campaign campaign) {
    }

    /**
     * Returns the default repair location constant for the unit.
     *
     * @return the repair location constant {@code Unit.SITE_FACILITY_BASIC}
     */
    public int getRepairLocation() {
        return Unit.SITE_FACILITY_BASIC;
    }

    /**
     * Retrieves the list of missions.
     *
     * <p><b>Note:</b> this returns the actual mission array. Any changes made to the array will be directly
     * modifying the version retained inside the {@link AbstractMissionTransition} object. If you just want to parse the
     * list {@link #getMissionsCopy()} is a safer option.</p>
     *
     * @return a list of Scenario objects.
     */
    public List<AbstractMission> getMissions() {
        return missions;
    }

    /**
     * Creates and returns an unmodifiable copy of the list of missions.
     *
     * @return an unmodifiable copy of the list of missions
     */
    public List<AbstractMission> getMissionsCopy() {
        return List.copyOf(missions);
    }

    /**
     * @param mission the mission to add this mission
     */
    public void addMission(final AbstractMission mission) {
        getMissions().add(mission);
    }

    public void writeToXML(Campaign campaign, final PrintWriter printWriter, int indent) {
        indent = writeToXMLBegin(campaign, printWriter, indent);
        writeToXMLEnd(printWriter, indent);
    }

    /**
     * Writes all {@link AbstractMissionTransition} fields to XML. Subclasses that have their own private fields must
     * override this, call {@code super.writeToXMLBegin(...)}, append only their private tags, and return the resulting
     * indent.
     */
    protected int writeToXMLBegin(Campaign campaign, final PrintWriter printWriter, int indent) {
        // opening tag and core identity
        MHQXMLUtility.writeSimpleXMLOpenTag(printWriter, indent++, "mission", "id", getId(), "type", getClass());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "name", getName());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "status", getStatus().name());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "desc", getDescription());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "id", getId());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "difficulty", getContractDifficulty());

        // contract financials and terms
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "employer", getEmployerName());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "employerCode", getEmployerCode());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "paymentMultiplier", getPaymentMultiplier());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "commandRights", getCommandRights().name());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "overheadComp", getOverheadCompensation());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "salvagePct", getSalvagePercent());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "salvageExchange", isSalvageExchange());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "straightSupport", getStraightSupport());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "battleLossComp", getBattleLossCompensation());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "transportComp", getTransportCompensation());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "mrbcFee", isPaidMRBCFee());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "advancePct", getAdvancePercent());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "signBonus", getSigningBonus());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "salvagedByUnit", getSalvagedByUnit());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "salvagedByEmployer", getSalvagedByEmployer());

        // combat requirements and state
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "sharesPct", getSharesPercent());

        // negotiation roll results
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "commandRoll", getContractNegotiationCommandRoll());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "salvageRoll", getContractNegotiationSalvageRoll());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "supportRoll", getContractNegotiationSupportRoll());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "transportRoll", getContractNegotiationTransportRoll());

        // NPCs
        if (getEmployerLiaison() != null) {
            MHQXMLUtility.writeSimpleXMLOpenTag(printWriter, indent++, "employerLiaison");
            getEmployerLiaison().writeToXMLHeadless(printWriter, indent, campaign);
            MHQXMLUtility.writeSimpleXMLCloseTag(printWriter, --indent, "employerLiaison");
        }

        return indent;
    }

    protected void writeToXMLEnd(final PrintWriter pw, int indent) {
        MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "mission");
    }

    protected void initializeFieldSetters() {
        // core identity
        fieldSetters.put("name",
              (v, c) -> setName(v.trim()));
        fieldSetters.put("status",
              (v, c) -> setStatus(MissionStatus.parseFromString(v.trim())));
        fieldSetters.put("id",
              (v, c) -> setId(Integer.parseInt(v.trim())));
        fieldSetters.put("desc",
              (v, c) -> setDescription(v.trim()));

        // contract financials and terms
        fieldSetters.put("employer",
              (v, c) -> setEmployerName(v.trim()));
        fieldSetters.put("paymentmultiplier",
              (v, c) -> setPaymentMultiplier(Double.parseDouble(v.trim())));
        fieldSetters.put("commandrights",
              (v, c) -> setCommandRights(ContractCommandRights.parseFromString(v.trim())));
        fieldSetters.put("overheadcomp",
              (v, c) -> setOverheadCompensation(Integer.parseInt(v.trim())));
        fieldSetters.put("salvagepct",
              (v, c) -> setSalvagePercent(Integer.parseInt(v.trim())));
        fieldSetters.put("salvageexchange",
              (v, c) -> setSalvageExchange(Boolean.parseBoolean(v.trim())));
        fieldSetters.put("straightsupport",
              (v, c) -> setStraightSupport(Integer.parseInt(v.trim())));
        fieldSetters.put("battlelosscomp",
              (v, c) -> setBattleLossCompensation(Integer.parseInt(v.trim())));
        fieldSetters.put("transportcomp",
              (v, c) -> setTransportCompensation(Integer.parseInt(v.trim())));
        fieldSetters.put("advancepct",
              (v, c) -> setAdvancePercent(Integer.parseInt(v.trim())));
        fieldSetters.put("signbonus",
              (v, c) -> setSigningBonus(Integer.parseInt(v.trim())));
        fieldSetters.put("mrbcfee",
              (v, c) -> setPaidMRBCFee(v.trim().equals("true")));
        fieldSetters.put("salvagedbyunit",
              (v, c) -> setSalvagedByUnit(Money.fromXmlString(v.trim())));
        fieldSetters.put("salvagedbyemployer",
              (v, c) -> setSalvagedByEmployer(Money.fromXmlString(v.trim())));

        // faction and force data
        fieldSetters.put("employercode",
              (v, c) -> setEmployerCode(v.trim()));
        fieldSetters.put("difficulty",
              (v, c) -> setContractDifficulty(Integer.parseInt(v.trim())));
        fieldSetters.put("sharespct",
              (v, c) -> setSharesPercent(Integer.parseInt(v.trim())));

        // negotiation roll results
        fieldSetters.put("commandroll",
              (v, c) -> setContractNegotiationCommandRoll(Integer.parseInt(v.trim())));
        fieldSetters.put("salvageroll",
              (v, c) -> setContractNegotiationSalvageRoll(Integer.parseInt(v.trim())));
        fieldSetters.put("supportroll",
              (v, c) -> setContractNegotiationSupportRoll(Integer.parseInt(v.trim())));
        fieldSetters.put("transportroll",
              (v, c) -> setContractNegotiationTransportRoll(Integer.parseInt(v.trim())));
    }

    protected void initializeNodeSetters() {
        // NPCs
        nodeSetters.put("employerliaison",
              (node, campaign, version) -> setEmployerLiaison(Person.generateInstanceFromXML(node, campaign, version)));
    }

    public void loadFieldsFromXmlNode(Campaign campaign, Version version, Node node) throws ParseException {
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node item = nodeList.item(i);

            if (item.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = item.getNodeName().trim().toLowerCase();

            try {
                BiConsumer<String, Campaign> fieldSetter = fieldSetters.get(nodeName);

                if (fieldSetter != null) {
                    fieldSetter.accept(item.getTextContent(), campaign);
                    continue;
                }

                AbstractMissionTransition.NodeSetter nodeSetter = nodeSetters.get(nodeName);

                if (nodeSetter != null) {
                    nodeSetter.accept(item, campaign, version);
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to load node {}", nodeName, ex);
            }
        }
    }

    /**
     * Instantiates the correct {@link AbstractMissionTransition} subclass from XML and fully loads its state. The
     * concrete type is determined by the {@code type} attribute on the node, identical to before.
     * <p>
     * Callers that previously used {@code Mission.generateInstanceFromXML} should migrate to this method; the static
     * delegate on {@link Mission} is preserved only for backward compatibility.
     */
    public static AbstractMissionTransition generateInstanceFromXML(Node node, Campaign campaign, Version version) {
        AbstractMissionTransition retVal = null;
        NamedNodeMap nodeAttributes = node.getAttributes();
        Node classNameNode = nodeAttributes.getNamedItem("type");
        String className = classNameNode.getTextContent();

        try {
            retVal = (AbstractMissionTransition) Class.forName(className).getDeclaredConstructor().newInstance();
            retVal.loadFieldsFromXmlNode(campaign, version, node);
        } catch (Exception ex) {
            LOGGER.error("", ex);
        }

        return retVal;
    }

    @Override
    public String toString() {
        return !getStatus().isCompleted() ?
                     getName() :
                     getFormattedTextAt(RESOURCE_BUNDLE, "AbstractContract.name.completed", getName());
    }

    @FunctionalInterface
    public interface NodeSetter {
        void accept(Node node, Campaign campaign, Version version);
    }
}
