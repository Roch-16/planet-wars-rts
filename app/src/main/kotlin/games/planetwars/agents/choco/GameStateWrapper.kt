package games.planetwars.agents.choco

import games.planetwars.core.GameState
import games.planetwars.core.Player
import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer
import games.planetwars.agents.choco.MCTSState
import games.planetwars.core.GameParams
import games.planetwars.core.ForwardModel
import games.planetwars.core.Planet

/**
 * Adaptador del estado real del juego para que pueda consumirse desde MCTS.
 *
 * Convierte GameState en la interfaz esperada por el árbol, generando acciones,
 * aplicando simulaciones con ForwardModel y evaluando los estados con una heurística.
 */

/**
 * Implementación concreta de MCTSState para Planet Wars.
 *
 * Encapsula el estado del juego, los parámetros de simulación y los ajustes
 * del árbol para evaluar acciones sin mutar el estado original.
 */
data class GameStateWrapper(
    val gameState: GameState,
    val playerId: Player,
    val params: GameParams = GameParams(),
    val mcts: MCTSParams = MCTSParams()
) : MCTSState {
    
    /** El estado es terminal si se alcanza el límite de ticks o si un jugador se queda sin planetas. */
    override fun isTerminal(): Boolean {
        // Igual que el forward model real: termina por tiempo o por eliminación.
        if (gameState.gameTick >= params.maxTicks) return true
        val hasPlayer1 = gameState.planets.any { it.owner == Player.Player1 }
        val hasPlayer2 = gameState.planets.any { it.owner == Player.Player2 }
        return !hasPlayer1 || !hasPlayer2
    }

    /**
     * Evalúa el estado para un jugador mezclando diferencia de naves,
     * control territorial y crecimiento potencial.
     *
     * Si el estado ya es terminal, devuelve una recompensa extrema en función
     * del total de naves acumuladas en ambos bandos.
     */
    override fun getResult(playerId: Player): Double {
        
        val opponent = playerId.opponent()

        // Naves en planetas y en tránsito para ambos jugadores.
        val myShips = gameState.planets
            .filter { it.owner == playerId }
            .sumOf { it.nShips }

        val oppShips = gameState.planets
            .filter { it.owner == opponent }
            .sumOf { it.nShips }

        val myTransitShips = gameState.planets
            .mapNotNull { it.transporter }
            .filter { it.owner == playerId }
            .sumOf { it.nShips }

        val oppTransitShips = gameState.planets
            .mapNotNull { it.transporter }
            .filter { it.owner == opponent }
            .sumOf { it.nShips }

        val myTotal = myShips + myTransitShips
        val oppTotal = oppShips + oppTransitShips

        // Crecimiento total por tick para ambos jugadores.
        val myGrowth = gameState.planets
            .filter { it.owner == playerId }
            .sumOf { it.growthRate }

        val oppGrowth = gameState.planets
            .filter { it.owner == opponent }
            .sumOf { it.growthRate }

        // Control territorial: planetas propios menos planetas rivales.
        val myPlanets = gameState.planets.count { it.owner == playerId }
        val oppPlanets = gameState.planets.count { it.owner == opponent }
        val territoryScore = (myPlanets - oppPlanets) * mcts.territoryWeight
            
        val progress = if (params.maxTicks > 0)
            gameState.gameTick.toDouble() / params.maxTicks
        else 0.0

        val growthWeight = if (progress < 0.5) mcts.earlyGrowthWeight else mcts.lateGrowthWeight
        val transitWeight = if (progress < 0.5) mcts.earlyTransitWeight else mcts.lateTransitWeight

        val shipDiff = (myTotal - oppTotal) / mcts.shipDiffDivisor

        // Si el juego terminó, usar una recompensa terminal clara.
        if (isTerminal()) {
            return when {
                myTotal > oppTotal -> mcts.terminalWinScore
                myTotal < oppTotal -> -mcts.terminalWinScore
                else -> 0.0
            }
        }

        // Heurística para estados no terminales.
        return shipDiff +
            growthWeight * (myGrowth - oppGrowth) +
            transitWeight * (myTransitShips - oppTransitShips) +
            territoryScore
    }
    
    /**
     * Clona el estado, aplica una acción propia y una respuesta simple del oponente.
     *
     * Se usa una copia porque MCTS necesita simular futuros sin mutar el estado
     * real que está evaluando el agente.
     */
    override fun takeAction(action: Action): MCTSState {
        val newState = gameState.deepCopy()
        val model = ForwardModel(newState, params)
        val oponent = playerId.opponent()

        val oponentAction = getActions(oponent).randomOrNull() ?: Action.doNothing()

        model.step(mapOf(playerId to action, oponent to oponentAction))

        return GameStateWrapper(newState, playerId, params, mcts)
    }
    
    /** Aplica una acción aleatoria del jugador actual para los rollouts. */
    override fun takeRandomAction(): MCTSState {
        val actions = getActions(playerId)
        if (actions.isEmpty()) return this
        val randomAction = actions.random()
        return takeAction(randomAction)
    }
    
    /**
     * Genera acciones candidatas priorizando defensa y luego ataque.
     *
     * Primero se crean refuerzos para planetas en peligro y después ataques
     * desde planetas propios sin transportador activo. La lista se recorta para
     * mantener controlada la expansión del árbol.
     */
    override fun getActions(playerId: Player): List<Action> {
       // Planetas disponibles para nuevas órdenes. 
       val myPlanets = gameState.planets.filter { it.owner == playerId && 
        it.transporter == null }
       val otherPlanets = gameState.planets.filter { it.owner != playerId } 
       val actions = mutableListOf<Action>() 

       // Generar acciones defensivas para planetas amenazados.
       val dangerPlanets = myPlanets.filter { isPlanetInDanger(it, playerId) }
       for (dangerPlanet in dangerPlanets) { 
        val defenseActions = defendPlanet(dangerPlanet, playerId)
        actions.addAll(defenseActions) 
       }

       // Generar acciones ofensivas desde cada planeta disponible.
       for (source in myPlanets) {
            val rankedTargets = otherPlanets 
            .map { target -> 
                val baseScore = targetScore(source, target, playerId)
                val distance = source.position.distance(target.position)
                 val shipsToSend = source.nShips * mcts.attackShipsFraction
                 val expectedDefense = target.nShips + target.growthRate * distance 
                val riskPenalty = if (shipsToSend < expectedDefense) 0.5 else 1.0
                target to (baseScore * riskPenalty) 
            } 
            .sortedByDescending { it.second } 
            .take(mcts.topTargetsPerSource) 
            .map { it.first }
            for (target in rankedTargets) { 
                attackPlanet(source, target, playerId)?.let { actions.add(it) } 
            } 
       }

       // Siempre dejar disponible la opción de no hacer nada.
       if (actions.isEmpty()) { actions.add(Action.doNothing()) }
       
       // Neutralizar el orden: barajar antes de recortar para que UCT decida qué explorar.
       if (actions.size > mcts.maxActionsPerState) { 
           return actions.shuffled().take(mcts.maxActionsPerState) 
       }

       return actions.shuffled()
    }

    /**
     * Puntúa objetivos de ataque priorizando enemigos, luego neutrales y,
     * por último, planetas propios.
     */
    private fun targetScore(source: Planet, target: Planet, playerId: Player): Double {
        val distance = source.position.distance(target.position)
        val shipsToSend = (source.nShips * mcts.attackShipsFraction).coerceAtLeast(1.0)

        val ownershipBonus = when (target.owner) {
            playerId.opponent() -> mcts.enemyTargetBonus    // Atacar enemigos es prioridad
            Player.Neutral -> mcts.neutralTargetBonus       // Luego neutrales
            else -> mcts.ownTargetBonus                     // Evitar atacar los propios
        }

        // Defensa esperada cuando llegue nuestro ataque.
        val expectedDefense = when (target.owner) {
            Player.Neutral -> target.nShips
            else -> target.nShips + target.growthRate * distance
        }

        // Viabilidad: premia ataques con superioridad y castiga los claramente inviables.
        val viabilityRatio = shipsToSend / (expectedDefense + 1.0)
        val viabilityFactor = viabilityRatio.coerceIn(0.35, 1.6)

        // Penaliza sobrecargar un objetivo que ya recibe refuerzos/ataques propios.
        val incomingFriendlyShips = gameState.planets
            .mapNotNull { it.transporter }
            .filter { it.owner == playerId && it.destinationIndex == target.id }
            .sumOf { it.nShips }
        val redundancyFactor = 1.0 / (1.0 + incomingFriendlyShips / (expectedDefense + 1.0))

        // En late-game aumenta la urgencia de expansión/ataque territorial.
        val ticksRemaining = (params.maxTicks - gameState.gameTick).coerceAtLeast(0)
        val urgency = if (params.maxTicks > 0) {
            1.0 - (ticksRemaining.toDouble() / params.maxTicks.toDouble())
        } else {
            0.0
        }
        val urgencyBoost = when (target.owner) {
            playerId -> 1.0
            else -> 1.0 + 0.5 * urgency
        }

        return ownershipBonus * target.growthRate * viabilityFactor * redundancyFactor * urgencyBoost / (distance + 1.0)
    }

    /** Indica si un ataque supera la defensa estimada del objetivo. */
    private fun isAttackViable(source: Planet, target: Planet): Boolean {
        val distance = source.position.distance(target.position)

        val shipsToSend = source.nShips * mcts.attackShipsFraction
        val expectedDefense = target.nShips + target.growthRate * distance

        return shipsToSend > expectedDefense
    }

    /** Detecta si un planeta recibe más naves enemigas de las que puede sostener. */
    private fun isPlanetInDanger(source: Planet, playerId: Player): Boolean {
        // Transportadores enemigos dirigidos a este planeta.
        val incomingEnemies = gameState.planets
            .filter { it.owner == playerId.opponent() }
            .mapNotNull { it.transporter }
            .filter { it.destinationIndex == source.id }

        // Total de naves enemigas en tránsito.
        val incomingShips = incomingEnemies.sumOf { it.nShips }
        
        // Hay peligro si superan la defensa local.
        return incomingShips > source.nShips
    }

    /** Genera refuerzos para un planeta amenazado priorizando defensores cercanos. */
    private fun defendPlanet(dangerPlanet: Planet, playerId: Player): List<Action> {
        val defenseActions = mutableListOf<Action>()
        
        // Cuántas naves enemigas están apuntando a este planeta.
        val incomingThreats = gameState.planets
            .mapNotNull { it.transporter }
            .filter { it.owner == playerId.opponent() }
            .filter { it.destinationIndex == dangerPlanet.id }
            .sumOf { it.nShips }

        // Naves necesarias para alcanzar paridad con los atacantes.
        val shipsNeeded = (incomingThreats - dangerPlanet.nShips).coerceAtLeast(1.0)

        // Planetas defensores ordenados por distancia.
        val defendingPlanets = gameState.planets
            .filter { it.owner == playerId && it.id != dangerPlanet.id && it.transporter == null }
            .sortedBy { it.position.distance(dangerPlanet.position) }

        var shipsToSend = shipsNeeded
        for (defender in defendingPlanets) {
            if (shipsToSend <= 0) break  // Ya cubierto el déficit
            if (defender.nShips <= mcts.minDefenseShips) continue  // Mantener defensa local mínima

            // Enviar hasta lo que necesitamos (máximo: todas menos defensa mínima)
            val availableShips = (defender.nShips - mcts.minDefenseShips).coerceAtMost(shipsToSend)
            if (availableShips > 0) {
                defenseActions.add(Action(playerId, defender.id, dangerPlanet.id, availableShips))
                shipsToSend -= availableShips
            }
        }

        return defenseActions
    }

    private fun attackPlanet(source: Planet, target: Planet, playerId: Player): Action? {
        //val distance = source.position.distance(target.position)
        val shipsToSend = (source.nShips * mcts.attackShipsFraction).coerceAtLeast(1.0)
        val viable = isAttackViable(source, target)

        // Mantener algo de exploración incluso cuando el ataque no parece rentable.
        if (!viable && Math.random() > 0.3) return null

        return Action(playerId, source.id, target.id, shipsToSend)
    }

    /** Evaluación rápida heurística para ajustar la exploración durante la selección. 
     * Esta función se llama durante la fase de selección para ajustar dinámicamente la constante de exploración.
     * Se basa en una evaluación rápida de la diferencia de naves y crecimiento entre ambos jugadores
    */
    override fun stateQuickEval(playerId: Player): Double {
        val opponent = playerId.opponent()

        val myShips = gameState.planets
            .filter { it.owner == playerId }
            .sumOf { it.nShips }

        val oppShips = gameState.planets
            .filter { it.owner == opponent }
            .sumOf { it.nShips }

        val myGrowth = gameState.planets
            .filter { it.owner == playerId }
            .sumOf { it.growthRate }

        val oppGrowth = gameState.planets
            .filter { it.owner == opponent }
            .sumOf { it.growthRate }

        val shipScore = (myShips - oppShips) / 100.0
        val growthScore = (myGrowth - oppGrowth) * 2.0

        return shipScore + growthScore
    }
    /**
     * Heurística de acción para progressive bias.
     * Reutiliza targetScore (calidad del objetivo) y lo extiende con rasgos de la acción concreta.
     */
    fun actionHeuristic(action: Action, playerId: Player): Double {
    if (action == Action.DO_NOTHING) return 0.05
    if (action.sourcePlanetId !in gameState.planets.indices) return 0.0
    if (action.destinationPlanetId !in gameState.planets.indices) return 0.0

    val source = gameState.planets[action.sourcePlanetId]
    val target = gameState.planets[action.destinationPlanetId]

    if (source.owner != playerId) return 0.0
    if (action.numShips <= 0.0) return 0.0

    // Base estratégica — ya incluye viabilidad, urgencia y redundancia.
    val baseTarget = targetScore(source, target, playerId)

    // Factibilidad suave (mejora tuya corregida).
    val feasibilityFactor = if (action.numShips <= source.nShips) {
        1.0
    } else {
        (source.nShips / action.numShips).coerceAtLeast(0.1)
    }

    // Señal defensiva — única señal nueva que no duplica targetScore.
    val defensiveBoost = if (target.owner == playerId && isPlanetInDanger(target, playerId)) {
        val distance = source.position.distance(target.position)
        1.5 / (distance + 1.0)
    } else 0.0

    return ((baseTarget + defensiveBoost) * feasibilityFactor).coerceAtLeast(0.0)
}
}