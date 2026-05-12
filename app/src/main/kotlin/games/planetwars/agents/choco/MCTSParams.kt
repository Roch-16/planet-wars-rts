package games.planetwars.agents.choco

data class MCTSParams(

    val maxIterations: Int = 200,
    val maxRolloutDepth: Int = 32,
    val maxActionsPerState: Int = 24,
    val timeBudgetMs: Long = 25,
    val explorationConstant: Double = 1.414,
    val terminalWinScore: Double = 10000.0,

    val attackShipsFraction: Double = 0.4,    // umbral de decisión moderado
    val attackShipsExecution: Double = 0.75,  // ataca con fuerza cuando decide

    val topTargetsPerSource: Int = 4,         // menos targets = árbol más profundo
    val minDefenseShips: Double = 5.0,        // defensa mínima razonable

    val territoryWeight: Double = 3.0,        // reducido, no dominar la heurística
    val shipDiffDivisor: Double = 15.0,       // normaliza bien diferencias de naves

    val earlyGrowthWeight: Double = 3.0,      // capturar planetas al inicio es clave
    val lateGrowthWeight: Double = 1.5,       // late game importa más las naves

    val earlyTransitWeight: Double = 0.5,
    val lateTransitWeight: Double = 1.0,

    val enemyTargetBonus: Double = 3.0,       // atacar enemigos es prioridad clara
    val neutralTargetBonus: Double = 1.5,     // neutrales secundarios
    val ownTargetBonus: Double = 0.1,         // casi nunca mover entre propios

    val biasWeight: Double = 0.15
)