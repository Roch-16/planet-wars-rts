package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 200,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 25,
    val explorationConstant: Double = 1.414,
    val terminalWinScore: Double = 10000.0,

    val attackShipsFraction: Double = 0.6858,
    val topTargetsPerSource: Int = 3,
    val minDefenseShips: Double = 14.8432,

    val territoryWeight: Double = 5.0516,
    val shipDiffDivisor: Double = 14.1233,
    val earlyGrowthWeight: Double = 4.3226,
    val lateGrowthWeight: Double = 2.0088,
    val earlyTransitWeight: Double = 1.9577,
    val lateTransitWeight: Double = 0.2474,
    
    val enemyTargetBonus: Double = 2.1793,
    val neutralTargetBonus: Double = 1.8984,
    val ownTargetBonus: Double = 0.4917
)