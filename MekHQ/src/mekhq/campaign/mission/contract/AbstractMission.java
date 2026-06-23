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

import static java.lang.Math.ceil;
import static megamek.client.ui.util.PlayerColour.BLUE;
import static megamek.client.ui.util.PlayerColour.RED;
import static megamek.common.enums.SkillLevel.REGULAR;
import static mekhq.campaign.mission.enums.AtBContractType.UNDEFINED;
import static mekhq.campaign.mission.enums.AtBMoraleLevel.MAXIMUM_MORALE_LEVEL;
import static mekhq.campaign.mission.enums.AtBMoraleLevel.MINIMUM_MORALE_LEVEL;
import static mekhq.campaign.mission.enums.AtBMoraleLevel.STALEMATE;
import static mekhq.campaign.personnel.ranks.Rank.RO_MIN;
import static mekhq.campaign.personnel.skills.SkillType.EXP_REGULAR;
import static mekhq.campaign.universe.Faction.INDEPENDENT_FACTION_CODE;
import static mekhq.utilities.MHQInternationalization.getFormattedTextAt;
import static mekhq.utilities.MHQInternationalization.getTextAt;

import java.io.PrintWriter;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import megamek.Version;
import megamek.client.ui.util.PlayerColour;
import megamek.common.enums.Gender;
import megamek.common.enums.SkillLevel;
import megamek.common.icons.Camouflage;
import megamek.logging.MMLogger;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.enums.DragoonRating;
import mekhq.campaign.finances.Accountant;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.AtBScenario;
import mekhq.campaign.mission.Mission;
import mekhq.campaign.mission.Scenario;
import mekhq.campaign.mission.TransportCostCalculations;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;
import mekhq.campaign.mission.enums.MissionStatus;
import mekhq.campaign.personnel.Bloodname;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.backgrounds.BackgroundsController;
import mekhq.campaign.personnel.enums.PersonnelRole;
import mekhq.campaign.personnel.enums.Phenotype;
import mekhq.campaign.personnel.ranks.AutoAssignRankForCompanyGenerator;
import mekhq.campaign.personnel.ranks.RankSystem;
import mekhq.campaign.personnel.ranks.RankValidator;
import mekhq.campaign.personnel.ranks.Ranks;
import mekhq.campaign.stratCon.StratConCampaignState;
import mekhq.campaign.stratCon.SupportPointNegotiation;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.Systems;
import mekhq.campaign.universe.factionStanding.FactionStandingUtilities;
import mekhq.utilities.MHQXMLUtility;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class AbstractMission {
    private static final MMLogger LOGGER = MMLogger.create(AbstractMission.class);
    private static final String RESOURCE_BUNDLE = "mekhq.resources.AbstractMission";

    private String name;
    private int id = -1; // TODO change to UUID
    private AbstractContract parentContract;
    private StratConCampaignState stratConCampaignState;
    private MissionStatus status = MissionStatus.ACTIVE;
    private AtBContractType contractType = UNDEFINED;
    private String contractTypeName = getTextAt(RESOURCE_BUNDLE, "AbstractMission.contractTypeName.default");

    private String systemId;
    private String legacyPlanetName;
    /*
     * This is a transient variable meant to keep track of a single jump path while the contract runs through initial
     * calculations, as the same jump path is referenced multiple times and calculating it each time is expensive. No
     * need to preserve it in save data.
     */
    private transient JumpPath cachedJumpPath = new JumpPath();

    private LocalDate startDate;
    private LocalDate endingDate;
    private int lengthInMonths = 1;

    private SkillLevel allySkill = REGULAR;
    private int allyQuality = DragoonRating.DRAGOON_C.getRating();
    private String allyBotName = getTextAt(RESOURCE_BUNDLE, "AbstractMission.allyBotName.default");
    private Camouflage allyCamouflage = new Camouflage(Camouflage.COLOUR_CAMOUFLAGE, PlayerColour.BLUE.name());
    private PlayerColour allyColour = BLUE;

    private String enemyCode = INDEPENDENT_FACTION_CODE;
    private String enemyName = getTextAt(RESOURCE_BUNDLE, "AbstractMission.belligerentName.default");
    private String enemyMercenaryEmployerCode;
    private Person clanOpponent;
    private boolean batchallAccepted = true;
    private SkillLevel enemySkill = REGULAR;
    private int enemyQuality = DragoonRating.DRAGOON_C.getRating();
    private String enemyBotName = getTextAt(RESOURCE_BUNDLE, "AbstractMission.enemyBotName.default");
    private Camouflage enemyCamouflage = new Camouflage(Camouflage.COLOUR_CAMOUFLAGE, PlayerColour.RED.name());
    private PlayerColour enemyColour = RED;

    private int contractDifficulty = 5;

    // actual amounts
    private Money advanceAmount = Money.zero();
    private Money signingBonusAmount = Money.zero();
    private Money overheadAmount = Money.zero();
    private Money supportAmount = Money.zero();
    private Money baseAmount = Money.zero();
    private Money feeAmount = Money.zero();
    private Money transportAmount = Money.zero();
    private Money transitAmount = Money.zero();

    private int hospitalBedsRented;
    private int kitchensRented;
    private int holdingCellsRented;
    private int partsAvailabilityLevel;

    private int requiredCombatTeams;
    private int requiredCombatElements;

    private boolean isPlayerAttacker;

    private AtBMoraleLevel moraleLevel = STALEMATE;
    private LocalDate routEndDate;
    private Money routedPayout = null;

    private final List<Scenario> scenarios = new ArrayList<>();

    protected static final Map<String, AbstractMission.FieldSetter> fieldSetters = initializeFieldSetters();
    protected static final Map<String, AbstractMission.NodeSetter> nodeSetters = initializeNodeSetters();

    public AbstractMission() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int uuid) {
        this.id = uuid;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public @Nullable PlanetarySystem getSystem() {
        return Systems.getInstance().getSystemById(getSystemId());
    }

    /**
     * Convenience property to return the name of the current planet. Sometimes, the "current planet" doesn't match up
     * with an existing planet in our planet database, in which case we return whatever was stored.
     */
    public String getSystemName(LocalDate when) {
        if (getSystem() == null) {
            return legacyPlanetName;
        }

        return getSystem().getName(when);
    }

    public @Nullable JumpPath getCachedJumpPath() {
        return cachedJumpPath;
    }

    public void setCachedJumpPath(JumpPath cachedJumpPath) {
        this.cachedJumpPath = cachedJumpPath;
    }

    /**
     * Gets the currently calculated jump path for this contract, only recalculating if it's not valid any longer or
     * hasn't been calculated yet.
     */
    public @Nullable JumpPath getJumpPath(Campaign campaign) {
        // if we don't have a cached jump path, or if the jump path's starting/ending point no longer match the
        // campaign's current location or contract's destination
        if ((getCachedJumpPath() == null) || getCachedJumpPath().isEmpty()
                  || !getCachedJumpPath().getFirstSystem().equals(campaign.getCurrentSystem())
                  || !getCachedJumpPath().getLastSystem().equals(getSystem())) {
            setCachedJumpPath(campaign.calculateJumpPath(campaign.getCurrentSystem(), getSystem()));
        }

        return getCachedJumpPath();
    }

    /**
     * Returns the contract length in months.
     *
     * @return the number and corresponding length of the contract in months as an integer
     */
    public int getLengthInMonths() {
        return lengthInMonths;
    }

    public void setLengthInMonths(int lengthInMonths) {
        this.lengthInMonths = lengthInMonths;
    }

    /**
     * Calculates the number of days required for travel based on the current campaign state.
     *
     * <p>This method determines if a valid destination system is set, computes if the command circuit should be used,
     * retrieves the jump path, and totals the travel time (including recharge, start, and end times). The result is
     * rounded to two decimal places and then to the nearest whole day.</p>
     *
     * @param campaign the {@link Campaign} instance containing context such as date, location, and command circuit
     *                 options
     *
     * @return the total number of travel days required; returns 0 if there is no valid system to travel to
     */
    public int getTravelDays(Campaign campaign) {
        if (null != this.getSystem()) {
            boolean isUseCommandCircuit =
                  FactionStandingUtilities.isUseCommandCircuit(campaign.isOverridingCommandCircuitRequirements(),
                        campaign.isGM(),
                        campaign.getCampaignOptions().isUseFactionStandingCommandCircuitSafe(),
                        campaign.getFactionStandings(), campaign.getFutureAtBContracts());

            JumpPath jumpPath = getJumpPath(campaign);
            if (jumpPath == null) {
                return 0;
            }

            double days = Math.round(jumpPath.getTotalTime(campaign.getLocalDate(),
                  campaign.getCurrentLocation().getTransitTime(), isUseCommandCircuit) * 100.0)
                                / 100.0;
            return (int) ceil(days);
        }
        return 0;
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the approximate number of months for a 2-way trip + deployment, rounded up
     */
    public int getLengthPlusTravel(Campaign campaign) {
        int travelMonths = (int) ceil(2 * getTravelDays(campaign) / 30.0);
        return getLengthInMonths() + travelMonths;
    }

    public @Nullable LocalDate getEndingDate() {
        return endingDate;
    }

    public void setEndingDate(@Nullable LocalDate endDate) {
        this.endingDate = endDate;
    }

    public @Nullable LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable LocalDate startDate) {
        this.startDate = startDate;
    }

    /**
     * Get the number of months left in this contract after the given date. Partial months are counted as full months.
     *
     * @param date the date to use in the calculation
     *
     * @return the number of months left
     */
    public int getMonthsLeft(LocalDate date) {
        if (getEndingDate() == null) {
            return 0;
        }

        int monthsLeft = Math.toIntExact(ChronoUnit.MONTHS.between(date, getEndingDate()));
        // Ensure partial months are counted based on the current day of the month, as
        // the above only
        // counts full months
        if (date.getDayOfMonth() != getEndingDate().getDayOfMonth()) {
            monthsLeft++;
        }
        return monthsLeft;
    }

    /**
     * This sets the Start Date and End Date of the Contract based on the length of the contract and the starting date
     * provided
     *
     * @param startDate the date the contract starts at
     */
    public void setStartAndEndDate(@Nonnull LocalDate startDate) {
        this.startDate = startDate;
        this.endingDate = startDate.plusMonths(getLengthInMonths());
    }

    public int getAllyQuality() {
        return allyQuality;
    }

    public void setAllyQuality(int allyQuality) {
        this.allyQuality = allyQuality;
    }

    public SkillLevel getAllySkill() {
        return allySkill;
    }

    public void setAllySkill(SkillLevel allySkill) {
        this.allySkill = allySkill;
    }

    public String getAllyBotName() {
        return allyBotName;
    }

    public void setAllyBotName(String allyBotName) {
        this.allyBotName = allyBotName;
    }

    public Camouflage getAllyCamouflage() {
        return allyCamouflage;
    }

    public void setAllyCamouflage(Camouflage allyCamouflage) {
        this.allyCamouflage = allyCamouflage;
    }

    public PlayerColour getAllyColour() {
        return allyColour;
    }

    public void setAllyColour(PlayerColour allyColour) {
        this.allyColour = allyColour;
    }

    public SkillLevel getEnemySkill() {
        return enemySkill;
    }

    public void setEnemySkill(SkillLevel enemySkill) {
        this.enemySkill = enemySkill;
    }

    public int getEnemyQuality() {
        return enemyQuality;
    }

    public void setEnemyQuality(int enemyQuality) {
        this.enemyQuality = enemyQuality;
    }

    public String getEnemyBotName() {
        return enemyBotName;
    }

    public void setEnemyBotName(String enemyBotName) {
        this.enemyBotName = enemyBotName;
    }

    public Camouflage getEnemyCamouflage() {
        return enemyCamouflage;
    }

    public void setEnemyCamouflage(Camouflage enemyCamouflage) {
        this.enemyCamouflage = enemyCamouflage;
    }

    public PlayerColour getEnemyColour() {
        return enemyColour;
    }

    public void setEnemyColour(PlayerColour enemyColour) {
        this.enemyColour = enemyColour;
    }

    public String getEnemyCode() {
        return enemyCode;
    }

    public void setEnemyCode(String enemyCode) {
        this.enemyCode = enemyCode;
    }

    public Faction getEnemy() {
        return Factions.getInstance().getFaction(getEnemyCode());
    }

    /**
     * Retrieves the name of the enemy for this contract.
     *
     * @param year The current year in the game.
     *
     * @return The name of the enemy.
     */
    public String generateEnemyName(int year) {
        Faction faction = Factions.getInstance().getFaction(getEnemyCode());

        if (faction.isMercenary() || faction.isPirate()) {
            if (Objects.equals(enemyBotName, "Enemy")) {
                return BackgroundsController.randomMercenaryCompanyNameGenerator(null);
            } else {
                return enemyBotName;
            }
        } else {
            return faction.getFullName(year);
        }
    }

    public @Nullable String getEnemyMercenaryEmployerCode() {
        return enemyMercenaryEmployerCode;
    }

    public @Nullable Faction getEnemyMercenaryEmployer() {
        return enemyMercenaryEmployerCode == null ? null :
                     Factions.getInstance().getFaction(enemyMercenaryEmployerCode);
    }

    /**
     * Sets the faction code representing the employer of the enemy mercenary forces.
     *
     * @param enemyMercenaryEmployerCode the faction code to assign as the employer of opposing mercenary units
     *
     * @author Illiani
     * @since 0.50.10
     */
    public void setEnemyMercenaryEmployerCode(@Nullable String enemyMercenaryEmployerCode) {
        this.enemyMercenaryEmployerCode = enemyMercenaryEmployerCode;
    }

    public int getContractDifficulty() {
        return contractDifficulty;
    }

    public void setContractDifficulty(int contractDifficulty) {
        this.contractDifficulty = contractDifficulty;
    }


    public Money getAdvanceAmount() {
        return advanceAmount;
    }

    public void setAdvanceAmount(Money advanceAmount) {
        this.advanceAmount = advanceAmount;
    }

    /**
     * @return total amount that will be paid on contract acceptance.
     */
    public Money getTotalAdvanceAmount() {
        return advanceAmount.plus(getSigningBonusAmount());
    }

    public Money getSigningBonusAmount() {
        return signingBonusAmount;
    }

    public void setSigningBonusAmount(Money signingBonusAmount) {
        this.signingBonusAmount = signingBonusAmount;
    }

    public Money getOverheadAmount() {
        return overheadAmount;
    }

    public void setOverheadAmount(Money overheadAmount) {
        this.overheadAmount = overheadAmount;
    }

    public Money getSupportAmount() {
        return supportAmount;
    }

    public void setSupportAmount(Money supportAmount) {
        this.supportAmount = supportAmount;
    }

    public Money getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(Money baseAmount) {
        this.baseAmount = baseAmount;
    }

    public Money getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(Money feeAmount) {
        this.feeAmount = feeAmount;
    }

    public Money getTransportAmount() {
        return transportAmount;
    }

    public void setTransportAmount(Money transportAmount) {
        this.transportAmount = transportAmount;
    }

    public Money getTransitAmount() {
        return transitAmount;
    }

    public void setTransitAmount(Money transitAmount) {
        this.transitAmount = transitAmount;
    }

    public Money getTotalAmountPlusFeesAndBonuses() {
        return getTotalAmountPlusFees().plus(getSigningBonusAmount());
    }

    public Money getTotalAmountPlusFees() {
        return getTotalAmount().minus(getFeeAmount());
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the cumulative sum the estimated monthly incomes - expenses
     */
    public Money getTotalMonthlyPayOut(Campaign campaign) {
        return getMonthlyPayOut()
                     .multipliedBy(getLengthInMonths())
                     .minus(getTotalEstimatedOverheadExpenses(campaign))
                     .minus(getTotalEstimatedMaintenanceExpenses(campaign))
                     .minus(getTotalEstimatedPayrollExpenses(campaign));
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the estimated payroll expenses for one month
     */
    public Money getEstimatedPayrollExpenses(Campaign campaign) {
        Accountant accountant = campaign.getAccountant();
        if (campaign.getCampaignOptions().isUsePeacetimeCost()) {
            return accountant.getPeacetimeCost();
        } else {
            return accountant.getPayRoll();
        }
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the cumulative sum of estimated payroll expenses for the duration of travel + deployment
     */
    public Money getTotalEstimatedPayrollExpenses(Campaign campaign) {
        return getEstimatedPayrollExpenses(campaign).multipliedBy(getLengthPlusTravel(campaign));
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the total (2-way) estimated transportation fee from the player's current location to this contract's
     *       planet
     */
    public Money getTotalTransportationFees(Campaign campaign) {
        if ((null != getSystem()) && campaign.getCampaignOptions().isPayForTransport()) {
            return getTransportCost(campaign, false);
        }

        return Money.zero();
    }

    /**
     * Calculates the employer's transport reimbursement based on the contract's transport compensation percentage.
     *
     * <p>This represents the amount the employer pays toward your transport costs. For example, if transport
     * compensation is 50% and the full transport cost is 1,000,000 C-bills, the employer reimburses you 500,000.</p>
     *
     * @param campaign the current {@link Campaign} used for transport cost calculation
     *
     * @return the {@link Money} amount the employer reimburses for transport
     */
    public Money getEmployerTransportReimbursement(Campaign campaign) {
        if ((null == getSystem()) || !campaign.getCampaignOptions().isPayForTransport()) {
            return Money.zero();
        }

        Money fullTransportCost = getTransportCost(campaign, false);
        return fullTransportCost.multipliedBy(parentContract.getTransportCompensation() / 100.0);
    }

    /**
     * Calculates the player's out-of-pocket transport cost after the employer's reimbursement.
     *
     * <p>This is the full transport cost minus the employer's reimbursement. For example, if transport compensation
     * is 50% and the full transport cost is 1,000,000 C-bills, the player pays 500,000.</p>
     *
     * @param campaign the current {@link Campaign} used for transport cost calculation
     *
     * @return the {@link Money} amount the player pays for transport after employer reimbursement
     */
    public Money getPlayerTransportCost(Campaign campaign) {
        if ((null == getSystem()) || !campaign.getCampaignOptions().isPayForTransport()) {
            return Money.zero();
        }

        Money fullTransportCost = getTransportCost(campaign, false);
        Money employerReimbursement = getEmployerTransportReimbursement(campaign);
        return fullTransportCost.minus(employerReimbursement);
    }

    /**
     * Calculates the total transport cost for this contract based on the campaign's transport settings and the
     * contract's jump path.
     *
     * <p>The calculation considers the following factors:</p>
     * <ul>
     *   <li>The jump path duration, including any command circuit adjustments</li>
     *   <li>The campaign's transport cost tables (using the Regular experience level)</li>
     *   <li>Whether the employer pays for a round trip (two-way pay)</li>
     *   <li>Whether transport compensation should be applied to reduce the final cost</li>
     * </ul>
     * <p>When {@code includeTransportCompensation} is true, the method calculates the employer's compensation
     * percentage and subtracts it from the final transport cost.</p>
     *
     * @param campaign                     the current {@link Campaign} used for jump path, transport options, and cost
     *                                     calculation
     * @param includeTransportCompensation whether to apply the contract's transport compensation percentage to reduce
     *                                     the cost
     *
     * @return the total {@link Money} required for transport, after applying all applicable modifiers
     */
    private Money getTransportCost(Campaign campaign, boolean includeTransportCompensation) {
        JumpPath jumpPath = getJumpPath(campaign);
        if (jumpPath == null) {
            return Money.zero();
        }

        TransportCostCalculations transportCostCalculations = campaign.getTransportCostCalculation(EXP_REGULAR);
        boolean useTwoWayPay = campaign.getCampaignOptions().isUseTwoWayPay();
        boolean isUseCommandCircuits = campaign.isUseCommandCircuitForContract(parentContract);
        int duration = (int) ceil(jumpPath.getTotalTime(campaign.getLocalDate(),
              campaign.getCurrentLocation().getTransitTime(), isUseCommandCircuits));
        Money transportCost = transportCostCalculations.calculateJumpCostForEntireJourney(duration,
              jumpPath.getJumps());

        // Is the employer paying for both ways?
        transportCost = transportCost.multipliedBy(useTwoWayPay ? 2 : 1);

        if (includeTransportCompensation) {
            Money transportCompensation = transportCost.multipliedBy(parentContract.getTransportCompensation() / 100.0);
            transportCost = transportCost.minus(transportCompensation);
        }

        return transportCost;
    }

    /**
     * Get the estimated total profit for this contract. The total profit is the total contract payment including fees
     * and bonuses, minus overhead, maintenance, payroll, spare parts, and other monthly expenses. The duration used for
     * monthly expenses is the contract duration plus the travel time from the unit's current world to the contract
     * world and back.
     *
     * <p>Transport costs are handled as follows: the employer's transport reimbursement is included in the contract
     * income (via {@link #getTotalAmount()}), and the full transport cost is subtracted here. The net effect is that
     * profit is reduced by the player's out-of-pocket transport cost (full cost minus employer reimbursement).</p>
     *
     * @param campaign The campaign with which this contract is associated.
     *
     * @return The estimated profit in the current default currency.
     */
    public Money getEstimatedTotalProfit(Campaign campaign) {
        return getTotalAdvanceAmount()
                     .plus(getTotalMonthlyPayOut(campaign))
                     .minus(getTotalTransportationFees(campaign));
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the cumulative sum of estimated maintenance expenses for the duration of travel + deployment
     */
    public Money getTotalEstimatedMaintenanceExpenses(Campaign campaign) {
        return campaign.getAccountant().getMaintenanceCosts().multipliedBy(getLengthPlusTravel(campaign));
    }

    /**
     * @param campaign campaign loaded
     *
     * @return the cumulative sum of estimated overhead expenses for the duration of travel + deployment
     */
    public Money getTotalEstimatedOverheadExpenses(Campaign campaign) {
        return campaign.getAccountant().getOverheadExpenses().multipliedBy(getLengthPlusTravel(campaign));
    }

    public Money getTotalAmount() {
        return getBaseAmount()
                     .plus(getSupportAmount())
                     .plus(getOverheadAmount())
                     .plus(getTransportAmount())
                     .plus(getTransitAmount());
    }

    public Money getMonthlyPayOut() {
        if (getLengthInMonths() <= 0) {
            return Money.zero();
        }

        return getTotalAmountPlusFeesAndBonuses()
                     .minus(getTotalAdvanceAmount())
                     .dividedBy(getLengthInMonths());
    }

    public String getEnemyName() {
        return enemyName;
    }

    public void setEnemyName(String enemyName) {
        this.enemyName = enemyName;
    }

    public @Nullable Person getClanOpponent() {
        return clanOpponent;
    }

    public void setClanOpponent(@Nullable Person clanOpponent) {
        this.clanOpponent = clanOpponent;
    }

    public void createClanOpponent(Campaign campaign) {
        setClanOpponent(campaign.newPerson(PersonnelRole.MEKWARRIOR, getEnemyCode(), Gender.RANDOMIZE));
        if (getClanOpponent() == null) {
            return;
        }

        Bloodname bloodname = Bloodname.randomBloodname(enemyCode, Phenotype.MEKWARRIOR, campaign.getGameYear());

        if (bloodname != null) {
            getClanOpponent().setBloodname(bloodname.getName());
        }

        AutoAssignRankForCompanyGenerator.assignAscendingRank(getClanOpponent(), RO_MIN);

        final RankSystem rankSystem = Ranks.getRankSystemFromCode("CLAN");

        final RankValidator rankValidator = new RankValidator();
        if (!rankValidator.validate(rankSystem, false)) {
            return;
        }

        getClanOpponent().setRankSystem(rankValidator, rankSystem);

        int targetClanRank = 38;
        AutoAssignRankForCompanyGenerator.assignAscendingRank(getClanOpponent(), targetClanRank);
    }

    public boolean isBatchallAccepted() {
        return batchallAccepted;
    }

    public void setBatchallAccepted(boolean batchallAccepted) {
        this.batchallAccepted = batchallAccepted;
    }

    public String getContractTypeName() {
        return contractTypeName;
    }

    public void setContractTypeName(String contractTypeName) {
        this.contractTypeName = contractTypeName;
    }

    public AtBContractType getContractType() {
        return contractType;
    }

    public void setContractTypeAndName(final AtBContractType contractType) {
        this.contractType = contractType;
        setContractTypeName(contractType.toString());
    }

    public void setContractType(final AtBContractType contractType) {
        this.contractType = contractType;
    }

    public boolean isPlayerAttacker() {
        return isPlayerAttacker;
    }

    public void setPlayerAttacker(boolean playerAttacker) {
        isPlayerAttacker = playerAttacker;
    }

    /**
     * Retrieves the list of scenarios.
     *
     * <p><b>Note:</b> this returns the actual scenario array. Any changes made to the array will be directly
     * modifying the version retained inside the {@link AbstractMission} object. If you just want to parse the list
     * {@link #getScenariosCopy()} is a safer option.</p>
     *
     * @return a list of Scenario objects.
     */
    public List<Scenario> getScenarios() {
        return scenarios;
    }

    /**
     * Creates and returns an unmodifiable copy of the list of scenarios.
     *
     * @return an unmodifiable copy of the list of scenarios
     */
    public List<Scenario> getScenariosCopy() {
        return List.copyOf(scenarios);
    }

    /**
     * Don't use this method directly as it will not add an id to the added scenario. Use Campaign#AddScenario instead
     *
     * @param scenario the scenario to add this mission
     */
    public void addScenario(final Scenario scenario) {
        scenario.setMissionId(id);
        getScenarios().add(scenario);
    }

    public List<Scenario> getVisibleScenarios() {
        return getScenarios().stream().filter(scenario -> !scenario.isCloaked()).collect(Collectors.toList());
    }

    public List<Scenario> getCurrentScenarios() {
        return getScenarios().stream()
                     .filter(scenario -> scenario.getStatus().isCurrent())
                     .collect(Collectors.toList());
    }

    public List<AtBScenario> getCurrentAtBScenarios() {
        return getScenarios().stream()
                     .filter(scenario -> scenario.getStatus().isCurrent() && (scenario instanceof AtBScenario))
                     .map(scenario -> (AtBScenario) scenario)
                     .collect(Collectors.toList());
    }

    public List<Scenario> getCompletedScenarios() {
        return getScenarios().stream()
                     .filter(scenario -> !scenario.getStatus().isCurrent())
                     .collect(Collectors.toList());
    }

    public void clearScenarios() {
        scenarios.clear();
    }

    public AbstractContract getParentContract() {
        return parentContract;
    }

    public void setParentContract(final AbstractContract parentContract) {
        this.parentContract = parentContract;
    }

    public StratConCampaignState getStratConCampaignState() {
        return stratConCampaignState;
    }

    public void setStratConCampaignState(StratConCampaignState stratConCampaignState) {
        this.stratConCampaignState = stratConCampaignState;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public String getLegacyPlanetName() {
        return legacyPlanetName;
    }

    public void setLegacyPlanetName(String legacyPlanetName) {
        this.legacyPlanetName = legacyPlanetName;
    }

    public int getHospitalBedsRented() {
        return hospitalBedsRented;
    }

    public void setHospitalBedsRented(int hospitalBedsRented) {
        this.hospitalBedsRented = hospitalBedsRented;
    }

    public int getKitchensRented() {
        return kitchensRented;
    }

    public void setKitchensRented(int kitchensRented) {
        this.kitchensRented = kitchensRented;
    }

    public int getHoldingCellsRented() {
        return holdingCellsRented;
    }

    public void setHoldingCellsRented(int holdingCellsRented) {
        this.holdingCellsRented = holdingCellsRented;
    }

    public int getPartsAvailabilityLevel() {
        return partsAvailabilityLevel;
    }

    public void setPartsAvailabilityLevel(int partsAvailabilityLevel) {
        this.partsAvailabilityLevel = partsAvailabilityLevel;
    }

    /**
     * Adjusts the 'parts availability level' by applying the specified delta value. This is a direct modifier to
     * procurement target numbers. This means that a negative delta is a <b>bonus</b> while a positive delta is a
     * <b>malus</b>.
     *
     * @param delta the amount to change the current parts availability level (can be positive or negative).
     */
    public void changePartsAvailabilityLevel(int delta) {
        partsAvailabilityLevel += delta;
    }

    public int getRequiredCombatElements() {
        return requiredCombatElements;
    }

    public void setRequiredCombatElements(int requiredCombatElements) {
        this.requiredCombatElements = requiredCombatElements;
    }

    public int getRequiredCombatTeams() {
        return requiredCombatTeams;
    }

    public void setRequiredCombatTeams(int requiredCombatTeams) {
        this.requiredCombatTeams = requiredCombatTeams;
    }

    /**
     * Returns the support-point reserve this contract can be negotiated up to, used as the "full reserves" reference
     * when displaying support points. This mirrors the cap applied during initial support-point negotiation (see
     * {@link SupportPointNegotiation}): three per required combat team.
     *
     * @return the maximum support points the contract can hold in reserve
     */
    public int getMaximumSupportPoints() {
        return requiredCombatTeams * 3;
    }

    public @Nullable LocalDate getRoutEndDate() {
        return routEndDate;
    }

    /**
     * Sets the end date of the rout. This should only be applied on contracts whose morale equals ROUTED
     *
     * @param routEnd the {@code LocalDate} representing the end date of the rout
     */
    public void setRoutEndDate(@Nullable LocalDate routEnd) {
        this.routEndDate = routEnd;
    }

    public AtBMoraleLevel getMoraleLevel() {
        return moraleLevel;
    }

    public void setMoraleLevel(AtBMoraleLevel moraleLevel) {
        this.moraleLevel = moraleLevel;
    }

    /**
     * Adjusts the current {@link AtBMoraleLevel} by the specified delta and returns the resulting morale level.
     *
     * <p>The method computes a new integer morale value by adding the given {@code delta} to the unit's current
     * morale level, then clamps the result to the valid range defined by {@code MINIMUM_MORALE_LEVEL} and
     * {@code MAXIMUM_MORALE_LEVEL}. It then attempts to resolve the resulting value to a corresponding
     * {@link AtBMoraleLevel}.</p>
     *
     * <p>If the resolved morale level is valid (i.e., non-{@code null}), the unit's internal morale state is updated.
     * If no valid enum constant exists for the computed level, the method leaves the current morale unchanged and
     * returns the existing level.</p>
     *
     * <p><b>Note:</b> a positive delta improves the enemy morale, a negative delta decreases enemy morale.</p>
     *
     * @param delta the amount to adjust the current morale level by; may be positive or negative
     *
     * @return the new {@link AtBMoraleLevel} after applying the delta; if no corresponding morale level exists for the
     *       computed value, the current morale level is returned unchanged
     *
     * @author Illiani
     * @since 0.50.10
     */
    public AtBMoraleLevel changeMoraleLevel(final int delta) {
        int currentLevel = getMoraleLevel().getLevel();
        int newLevel = Math.clamp(currentLevel + delta, MINIMUM_MORALE_LEVEL, MAXIMUM_MORALE_LEVEL);

        AtBMoraleLevel newMoraleLevel = AtBMoraleLevel.parseFromLevel(newLevel);
        if (newMoraleLevel != null) {
            setMoraleLevel(newMoraleLevel);
        }

        return newMoraleLevel != null ? newMoraleLevel : getMoraleLevel();
    }

    public boolean isPeaceful() {
        return getContractType().isGarrisonType() && getMoraleLevel().isRouted();
    }

    public @Nullable Money getRoutedPayout() {
        return routedPayout;
    }

    public void setRoutedPayout(@Nullable Money routedPayout) {
        this.routedPayout = routedPayout;
    }

    public void writeToXML(Campaign campaign, final PrintWriter printWriter, int indent) {
        indent = writeToXMLBegin(campaign, printWriter, indent);
        writeToXMLEnd(printWriter, indent);
    }

    /**
     * Writes all {@link AbstractMission} fields to XML. Subclasses that have their own private fields must override
     * this, call {@code super.writeToXMLBegin(...)}, append only their private tags, and return the resulting indent.
     */
    protected int writeToXMLBegin(Campaign campaign, final PrintWriter printWriter, int indent) {
        // opening tag and core identity
        MHQXMLUtility.writeSimpleXMLOpenTag(printWriter, indent++, "mission", "id", getId(), "type", getClass());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "name", getName());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "type", getContractTypeName());
        if (getSystemId() != null) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "systemId", getSystemId());
        } else {
            MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "planetName", getLegacyPlanetName());
        }
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "status", getStatus().name());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "id", getId());

        // scenarios block
        MHQXMLUtility.writeSimpleXMLOpenTag(printWriter, indent++, "scenarios");
        for (Scenario s : getScenarios()) {
            s.writeToXML(printWriter, indent);
        }
        MHQXMLUtility.writeSimpleXMLCloseTag(printWriter, --indent, "scenarios");

        // contract financials and terms
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "nMonths", getLengthInMonths());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "startDate", getStartDate());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "endDate", getEndingDate());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "hospitalBedsRented", getHospitalBedsRented());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "kitchensRented", getKitchensRented());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "holdingCellsRented", getHoldingCellsRented());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "advanceAmount", getAdvanceAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "signingAmount", getSigningBonusAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "transportAmount", getTransportAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "transitAmount", getTransitAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "overheadAmount", getOverheadAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "supportAmount", getSupportAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "baseAmount", getBaseAmount());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "feeAmount", getFeeAmount());

        // faction and force data
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "enemyCode", getEnemyCode());
        if (getEnemyMercenaryEmployerCode() != null) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter,
                  indent,
                  "enemyMercenaryEmployerCode",
                  getEnemyMercenaryEmployerCode());
        }
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "contractType", getContractType().name());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "allySkill", getAllySkill().name());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "allyQuality", getAllyQuality());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "enemySkill", getEnemySkill().name());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "enemyQuality", getEnemyQuality());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "difficulty", getContractDifficulty());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "allyBotName", getAllyBotName());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "enemyBotName", getEnemyBotName());
        if (!getAllyCamouflage().hasDefaultCategory()) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "allyCamoCategory", getAllyCamouflage().getCategory());
        }
        if (!getAllyCamouflage().hasDefaultFilename()) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "allyCamoFileName", getAllyCamouflage().getFilename());
        }
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "allyColour", getAllyColour().name());
        if (!getEnemyCamouflage().hasDefaultCategory()) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter,
                  indent,
                  "enemyCamoCategory",
                  getEnemyCamouflage().getCategory());
        }
        if (!getEnemyCamouflage().hasDefaultFilename()) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter,
                  indent,
                  "enemyCamoFileName",
                  getEnemyCamouflage().getFilename());
        }
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "enemyColour", getEnemyColour().name());

        // combat requirements and state
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "requiredCombatTeams", getRequiredCombatTeams());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "requiredCombatElements", getRequiredCombatElements());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "moraleLevel", getMoraleLevel().name());
        if (getRoutEndDate() != null) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "routEnd", getRoutEndDate());
        }
        if (getRoutedPayout() != null) {
            MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "routedPayout", getRoutedPayout());
        }
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "partsAvailabilityLevel", getPartsAvailabilityLevel());
        MHQXMLUtility.writeSimpleXMLTag(printWriter, indent, "batchallAccepted", isBatchallAccepted());

        // StratCon state
        if (getStratConCampaignState() != null) {
            getStratConCampaignState().Serialize(printWriter);
        }

        // NPCs
        if (getClanOpponent() != null) {
            MHQXMLUtility.writeSimpleXMLOpenTag(printWriter, indent++, "clanOpponent");
            getClanOpponent().writeToXMLHeadless(printWriter, indent, campaign);
            MHQXMLUtility.writeSimpleXMLCloseTag(printWriter, --indent, "clanOpponent");
        }

        return indent;
    }

    protected void writeToXMLEnd(final PrintWriter pw, int indent) {
        MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "mission");
    }

    protected static Map<String, AbstractMission.FieldSetter> initializeFieldSetters() {
        Map<String, AbstractMission.FieldSetter> fieldSetters = new HashMap<>();

        // core identity
        fieldSetters.put("name",
              (mission, v, c) -> mission.setName(v.trim()));
        fieldSetters.put("planetid",
              (mission, v, c) -> mission.setSystemId(v.trim()));
        fieldSetters.put("systemid",
              (mission, v, c) -> mission.setSystemId(v.trim()));
        fieldSetters.put("planetname",
              (mission, v, c) -> mission.parsePlanetName(v, c));
        fieldSetters.put("status",
              (mission, v, c) -> mission.setStatus(MissionStatus.parseFromString(v.trim())));
        fieldSetters.put("id",
              (mission, v, c) -> mission.setId(Integer.parseInt(v.trim())));
        fieldSetters.put("type",
              (mission, v, c) -> mission.setContractTypeName(v.trim()));

        // contract financials and terms
        fieldSetters.put("startdate",
              (mission, v, c) -> mission.setStartDate(MHQXMLUtility.parseDate(v.trim())));
        fieldSetters.put("enddate",
              (mission, v, c) -> mission.setEndingDate(MHQXMLUtility.parseDate(v.trim())));
        fieldSetters.put("nmonths",
              (mission, v, c) -> mission.setLengthInMonths(Integer.parseInt(v.trim())));
        fieldSetters.put("hospitalbedsrented",
              (mission, v, c) -> mission.setHospitalBedsRented(Integer.parseInt(v.trim())));
        fieldSetters.put("kitchensrented",
              (mission, v, c) -> mission.setKitchensRented(Integer.parseInt(v.trim())));
        fieldSetters.put("holdingcellsrented",
              (mission, v, c) -> mission.setHoldingCellsRented(Integer.parseInt(v.trim())));
        fieldSetters.put("advanceamount",
              (mission, v, c) -> mission.setAdvanceAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("signingamount",
              (mission, v, c) -> mission.setSigningBonusAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("transportamount",
              (mission, v, c) -> mission.setTransportAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("transitamount",
              (mission, v, c) -> mission.setTransitAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("overheadamount",
              (mission, v, c) -> mission.setOverheadAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("supportamount",
              (mission, v, c) -> mission.setSupportAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("baseamount",
              (mission, v, c) -> mission.setBaseAmount(Money.fromXmlString(v.trim())));
        fieldSetters.put("feeamount",
              (mission, v, c) -> mission.setFeeAmount(Money.fromXmlString(v.trim())));

        // faction and force data
        fieldSetters.put("enemycode",
              (mission, v, c) -> mission.setEnemyCode(v.trim()));
        fieldSetters.put("enemymercenaryemployercode",
              (mission, v, c) -> mission.setEnemyMercenaryEmployerCode(v.trim()));
        fieldSetters.put("contracttype",
              (mission, v, c) -> mission.setContractType(AtBContractType.parseFromString(v.trim())));
        fieldSetters.put("allyskill",
              (mission, v, c) -> mission.setAllySkill(SkillLevel.parseFromString(v.trim())));
        fieldSetters.put("allyquality",
              (mission, v, c) -> mission.setAllyQuality(Integer.parseInt(v.trim())));
        fieldSetters.put("enemyskill",
              (mission, v, c) -> mission.setAllySkill(SkillLevel.parseFromString(v.trim())));
        fieldSetters.put("allyquality",
              (mission, v, c) -> mission.setAllyQuality(Integer.parseInt(v.trim())));
        fieldSetters.put("enemyskill",
              (mission, v, c) -> mission.setEnemySkill(SkillLevel.parseFromString(v.trim())));
        fieldSetters.put("enemyquality",
              (mission, v, c) -> mission.setEnemyQuality(Integer.parseInt(v.trim())));
        fieldSetters.put("difficulty",
              (mission, v, c) -> mission.setContractDifficulty(Integer.parseInt(v.trim())));
        fieldSetters.put("allybotname",
              (mission, v, c) -> mission.setAllyBotName(v.trim()));
        fieldSetters.put("enemybotname",
              (mission, v, c) -> mission.setEnemyBotName(v.trim()));
        fieldSetters.put("allycamocategory",
              (mission, v, c) -> mission.getAllyCamouflage().setCategory(v.trim()));
        fieldSetters.put("allycamofilename",
              (mission, v, c) -> mission.getAllyCamouflage().setFilename(v.trim()));
        fieldSetters.put("allycolour",
              (mission, v, c) -> mission.setAllyColour(PlayerColour.parseFromString(v.trim())));
        fieldSetters.put("enemycamocategory",
              (mission, v, c) -> mission.getEnemyCamouflage().setCategory(v.trim()));
        fieldSetters.put("enemycamofilename",
              (mission, v, c) -> mission.getEnemyCamouflage().setFilename(v.trim()));
        fieldSetters.put("enemycolour",
              (mission, v, c) -> mission.setEnemyColour(PlayerColour.parseFromString(v.trim())));
        fieldSetters.put("requiredcombatteams",
              (mission, v, c) -> mission.setRequiredCombatTeams(Integer.parseInt(v.trim())));
        fieldSetters.put("requiredcombatelements",
              (mission, v, c) -> mission.setRequiredCombatElements(Integer.parseInt(v.trim())));
        fieldSetters.put("moralelevel",
              (mission, v, c) -> mission.setMoraleLevel(AtBMoraleLevel.parseFromString(v.trim())));
        fieldSetters.put("routend",
              (mission, v, c) -> mission.setRoutEndDate(MHQXMLUtility.parseDate(v.trim())));
        fieldSetters.put("routedpayout",
              (mission, v, c) -> mission.setRoutedPayout(Money.fromXmlString(v.trim())));
        fieldSetters.put("partsavailabilitylevel",
              (mission, v, c) -> mission.setPartsAvailabilityLevel(Integer.parseInt(v.trim())));
        fieldSetters.put("batchallaccepted",
              (mission, v, c) -> mission.setBatchallAccepted(Boolean.parseBoolean(v.trim())));

        return fieldSetters;
    }

    private void parsePlanetName(String value, Campaign campaign) {
        PlanetarySystem system = campaign.getSystemByName(value);

        if (system != null) {
            setSystemId(system.getId());
        } else {
            setLegacyPlanetName(value);
        }
    }

    protected static Map<String, AbstractMission.NodeSetter> initializeNodeSetters() {
        Map<String, AbstractMission.NodeSetter> nodeSetters = new HashMap<>();

        // scenario
        nodeSetters.put("scenarios", (node, campaign, mission, version) -> mission.scenarioNodeSetter(version, node,
              campaign));

        // StratCon state
        nodeSetters.put(StratConCampaignState.ROOT_XML_ELEMENT_NAME.toLowerCase(),
              (node, campaign, mission, version) -> mission.setStratConCampaignState(StratConCampaignState.Deserialize(
                    node)));

        // NPCs
        nodeSetters.put("clanopponent",
              (node, campaign, mission, version) -> mission.setClanOpponent(Person.generateInstanceFromXML(node,
                    campaign, version)));

        return nodeSetters;
    }

    private void scenarioNodeSetter(Version version, Node node, Campaign campaign) {
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node scenarioNode = children.item(i);

            if (scenarioNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            if (!scenarioNode.getNodeName().equalsIgnoreCase("scenario")) {
                LOGGER.error(
                      "Unknown node type not loaded in Scenario nodes: {}",
                      scenarioNode.getNodeName());
                continue;
            }

            Scenario scenario = Scenario.generateInstanceFromXML(scenarioNode, campaign, version);

            if (scenario != null) {
                addScenario(scenario);
            }
        }
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
                AbstractMission.FieldSetter fieldSetter = fieldSetters.get(nodeName);

                if (fieldSetter != null) {
                    fieldSetter.accept(this, item.getTextContent(), campaign);
                    continue;
                }

                AbstractMission.NodeSetter nodeSetter = nodeSetters.get(nodeName);

                if (nodeSetter != null) {
                    nodeSetter.accept(node, campaign, this, version);
                }
            } catch (Exception ex) {
                LOGGER.error("Failed to load node {}", nodeName, ex);
            }
        }
    }

    /**
     * Instantiates the correct {@link AbstractMission} subclass from XML and fully loads its state. The concrete type
     * is determined by the {@code type} attribute on the node, identical to before.
     * <p>
     * Callers that previously used {@code Mission.generateInstanceFromXML} should migrate to this method; the static
     * delegate on {@link Mission} is preserved only for backward compatibility.
     */
    public static AbstractMission generateInstanceFromXML(Node node, Campaign campaign, Version version) {
        AbstractMission retVal = null;
        NamedNodeMap nodeAttributes = node.getAttributes();
        Node classNameNode = nodeAttributes.getNamedItem("type");
        String className = classNameNode.getTextContent();

        try {
            retVal = (AbstractMission) Class.forName(className).getDeclaredConstructor().newInstance();
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
                     getFormattedTextAt(RESOURCE_BUNDLE, "AbstractMission.name.completed", getName());
    }

    @FunctionalInterface
    public interface NodeSetter {
        void accept(Node node, Campaign campaign, AbstractMission mission, Version version);
    }

    @FunctionalInterface
    public interface FieldSetter {
        void accept(AbstractMission mission, String value, Campaign campaign);
    }
}
