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

import static megamek.client.ui.util.PlayerColour.BLUE;
import static megamek.client.ui.util.PlayerColour.RED;
import static megamek.common.enums.SkillLevel.REGULAR;
import static mekhq.campaign.mission.enums.AtBContractType.UNDEFINED;
import static mekhq.campaign.mission.enums.AtBMoraleLevel.STALEMATE;
import static mekhq.campaign.universe.Faction.INDEPENDENT_FACTION_CODE;
import static mekhq.utilities.MHQInternationalization.getTextAt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import megamek.client.ui.util.PlayerColour;
import megamek.common.enums.SkillLevel;
import megamek.common.icons.Camouflage;
import megamek.logging.MMLogger;
import mekhq.campaign.JumpPath;
import mekhq.campaign.enums.DragoonRating;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.Scenario;
import mekhq.campaign.mission.enums.AtBContractType;
import mekhq.campaign.mission.enums.AtBMoraleLevel;
import mekhq.campaign.mission.enums.MissionStatus;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.stratCon.StratConCampaignState;

public abstract class AbstractMission {
    private static final MMLogger LOGGER = MMLogger.create(AbstractContract.class);
    private static final String RESOURCE_BUNDLE = "mekhq.resources.AbstractMission";

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
    private transient JumpPath cachedJumpPath;

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
}
