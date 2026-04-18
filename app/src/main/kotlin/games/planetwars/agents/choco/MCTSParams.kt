package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 200,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 25,
    val explorationConstant: Double = 1.414,

    val topTargetsPerSource: Int = 5,
    val attackShipsFraction: Double = 0.5,
    val minDefenseShips: Double = 5.0,

    val enemyTargetBonus: Double = 2.0,
    val neutralTargetBonus: Double = 1.0,
    val ownTargetBonus: Double = 0.1,

    val territoryWeight: Double = 10.0,
    val shipDiffDivisor: Double = 10.0,
    val earlyGrowthWeight: Double = 3.0,
    val lateGrowthWeight: Double = 1.5,
    val earlyTransitWeight: Double = 0.3,
    val lateTransitWeight: Double = 0.8,

    val terminalWinScore: Double = 10000.0
)