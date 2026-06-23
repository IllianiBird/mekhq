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

import static mekhq.campaign.mission.enums.ContractCommandRights.INDEPENDENT;
import static mekhq.campaign.universe.Faction.INDEPENDENT_FACTION_CODE;
import static mekhq.utilities.MHQInternationalization.getTextAt;

import megamek.logging.MMLogger;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.enums.ContractCommandRights;
import mekhq.campaign.mission.enums.MissionStatus;
import mekhq.campaign.personnel.Person;

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

    // actual amounts
    private Money advanceAmount = Money.zero();
    private Money signingBonusAmount = Money.zero();
    private Money transportAmount = Money.zero();
    private Money transitAmount = Money.zero();
    private Money overheadAmount = Money.zero();
    private Money supportAmount = Money.zero();
    private Money baseAmount = Money.zero();
    private Money feeAmount = Money.zero();

    private boolean paidMRBCFee = true;
    private int mrbcFeePercent = MRBC_FEE_PERCENTAGE;
    private int sharesPercent = DEFAULT_SHARES_PERCENT;
    private int advancePercent;
    private int signingBonus;

    private int contractNegotiationCommandRoll;
    private int contractNegotiationSalvageRoll;
    private int contractNegotiationSupportRoll;
    private int contractNegotiationTransportRoll;
}
