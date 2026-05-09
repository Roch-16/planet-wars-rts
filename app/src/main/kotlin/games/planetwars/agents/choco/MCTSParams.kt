package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 200,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 25,
    val explorationConstant: Double = 1.414,
    val terminalWinScore: Double = 10000.0,

    val attackShipsFraction: Double = 0.3298,
    val topTargetsPerSource: Int = 5,
    val minDefenseShips: Double = 14.4399,

    val territoryWeight: Double = 7.092,
    val shipDiffDivisor: Double = 10.7954,
    val earlyGrowthWeight: Double = 1.2318,
    val lateGrowthWeight: Double = 2.8545,
    val earlyTransitWeight: Double = 0.7119,
    val lateTransitWeight: Double = 1.3887,
    
    val enemyTargetBonus: Double = 2.4676,
    val neutralTargetBonus: Double = 1.9493,
    val ownTargetBonus: Double = 0.0187,

    // Peso bajo recomendado para progressive bias.
    val biasWeight: Double = 0.3635
)