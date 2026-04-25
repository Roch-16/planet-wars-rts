package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 200,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 25,
    val explorationConstant: Double = 1.414,
    val terminalWinScore: Double = 10000.0,

    val attackShipsFraction: Double = 0.5288,
    val topTargetsPerSource: Int = 3,
    val minDefenseShips: Double = 14.4691,

    val territoryWeight: Double = 12.1674,
    val shipDiffDivisor: Double = 10.7607,
    val earlyGrowthWeight: Double = 3.2106,
    val lateGrowthWeight: Double = 1.6235,
    val earlyTransitWeight: Double = 0.3,
    val lateTransitWeight: Double = 0.8,
    
    val enemyTargetBonus: Double = 2.0,
    val neutralTargetBonus: Double = 1.0,
    val ownTargetBonus: Double = 0.1
)