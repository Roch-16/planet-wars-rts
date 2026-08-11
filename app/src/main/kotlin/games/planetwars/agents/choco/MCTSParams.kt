package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 120,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 15,
    val explorationConstant: Double = 1.414,
    val terminalWinScore: Double = 10000.0,

    val attackShipsFraction: Double = 0.45,    // umbral de decisión moderado
    val attackShipsExecution: Double = 0.7966,  // ataca con fuerza cuando decide

    val topTargetsPerSource: Int = 3,         // menos targets = árbol más profundo
    val minDefenseShips: Double = 5.0101,        // defensa mínima razonable

    val territoryWeight: Double = 7.411,        // reducido, no dominar la heurística
    val shipDiffDivisor: Double = 12.0428,       // normaliza bien diferencias de naves

    val earlyGrowthWeight: Double = 4.8437,      // capturar planetas al inicio es clave
    val lateGrowthWeight: Double = 3.6769,       // late game importa más las naves

    val earlyTransitWeight: Double = 1.1226,
    val lateTransitWeight: Double = 0.4185,

    val enemyTargetBonus: Double = 2.7646,       // atacar enemigos es prioridad clara
    val neutralTargetBonus: Double = 1.9737,     // neutrales secundarios
    val ownTargetBonus: Double = 0.2169,         // casi nunca mover entre propios

    val biasWeight: Double = 0.0607
)