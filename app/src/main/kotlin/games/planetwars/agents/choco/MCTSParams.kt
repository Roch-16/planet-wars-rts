package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 200,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 25,
    val explorationConstant: Double = 1.414,
    val terminalWinScore: Double = 10000.0,

    val attackShipsFraction: Double = 0.7891,
    val topTargetsPerSource: Int = 8,
    val minDefenseShips: Double = 8.6983,

    val territoryWeight: Double = 12.2298,
    val shipDiffDivisor: Double = 15.3952,
    val earlyGrowthWeight: Double = 1.1134,
    val lateGrowthWeight: Double = 1.6732,
    val earlyTransitWeight: Double = 1.5373,
    val lateTransitWeight: Double = 1.1562,
    
    val enemyTargetBonus: Double = 1.0551,
    val neutralTargetBonus: Double = 1.637,
    val ownTargetBonus: Double = 0.0893
)