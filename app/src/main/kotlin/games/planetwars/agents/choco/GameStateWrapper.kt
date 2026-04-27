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
 * MCTS (Monte Carlo Tree Search) para Planet Wars RTS
 * 
 * Este módulo implementa un motor de búsqueda que explora posibles futuros del juego
 * usando simulaciones aleatorias y estadísticas para encontrar las mejores acciones.
 * 
 * Arquitectura:
 * - MCTSState: Interfaz que define qué operaciones debe soportar un estado (isTerminal, getActions, etc)
 * - GameStateWrapper: Adapta GameState (motor de juego) para que funcione con MCTS
 * - MonteCarloTreeSearch: Motor de búsqueda que explora el árbol de decisiones
 * 
 * Flujo de decisión:
 * 1. TryAgent pide una acción
 * 2. MonteCarloTreeSearch.run() explora múltiples futuros
 * 3. GameStateWrapper genera acciones (defensa primero, luego ataque)
 * 4. Se simula cada acción y se evalúa el resultado
n * 5. Se retorna la acción con mejor promedio de ganancia
 */

/**
 * GameStateWrapper: Adaptador del estado del juego para MCTS (Monte Carlo Tree Search)
 * Implementa la interfaz MCTSState para permitir simulaciones y búsqueda de árbol.
 * 
 * Este wrapper encapsula:
 * - gameState: El estado actual del juego
 * - playerId: El jugador que estamos optimizando (nosotros)
 * - params: Los parámetros del juego (velocidad transporters, ticks máximos, etc)
 * - mcts: Los parámetros de ajuste MCTS (iteraciones, profundidad, etc)
 */
data class GameStateWrapper(
    val gameState: GameState,
    val playerId: Player,
    val params: GameParams = GameParams(),
    val mcts: MCTSParams = MCTSParams()
) : MCTSState {
    
    /**
     * isTerminal: Determina si el juego ha terminado
     * El juego termina si:
     * 1. Se alcanzó el límite de ticks (tiempo máximo)
     * 2. Un jugador perdió todos sus planetas (eliminación)
     */
    override fun isTerminal(): Boolean {
        // Igual que el forward model real: termina por tiempo o por eliminacion.
        if (gameState.gameTick >= params.maxTicks) return true
        // Juego termina cuando uno de los jugadores no tiene planetas
        val hasPlayer1 = gameState.planets.any { it.owner == Player.Player1 }
        val hasPlayer2 = gameState.planets.any { it.owner == Player.Player2 }
        return !hasPlayer1 || !hasPlayer2
    }

    /**
     * getResult: Evalúa la calidad de un estado para el jugador dado
     * Usa una función heurística que considera:
     * - Diferencia de naves (en planetas + en tránsito)
     * - Crecimiento potencial (growthRate de turros futuros)
     * - Progreso del juego: enfatiza crecimiento al inicio, y naves al final
     * 
     * Retorna:
     * - Valores altos (>0) si estamos ganando
     * - Valores bajos (<0) si estamos perdiendo
     * - 10000 si ganamos (mejor que perder por diferencia pequeña)
     * - -10000 si perdemos
     */
    override fun getResult(playerId: Player): Double {
        
        val opponent = playerId.opponent()

        // Contar naves en planetas y en tránsito para ambos jugadores
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

        // Crecimiento total por tick para ambos jugadores
        val myGrowth = gameState.planets
            .filter { it.owner == playerId }
            .sumOf { it.growthRate }

        val oppGrowth = gameState.planets
            .filter { it.owner == opponent }
            .sumOf { it.growthRate }

        // Control territorial: cuántos planetas controlamos vs el rival
        val myPlanets = gameState.planets.count { it.owner == playerId }
        val oppPlanets = gameState.planets.count { it.owner == opponent }
        val territoryScore = (myPlanets - oppPlanets) * mcts.territoryWeight
            
        val progress = if (params.maxTicks > 0)
            gameState.gameTick.toDouble() / params.maxTicks
        else 0.0

        val growthWeight = if (progress < 0.5) mcts.earlyGrowthWeight else mcts.lateGrowthWeight
        val transitWeight = if (progress < 0.5) mcts.earlyTransitWeight else mcts.lateTransitWeight

        val shipDiff = (myTotal - oppTotal) / mcts.shipDiffDivisor

        // Si el juego ha terminado, asignar un valor alto o bajo dependiendo del resultado
        if (isTerminal()) {
            return when {
                myTotal > oppTotal -> mcts.terminalWinScore
                myTotal < oppTotal -> -mcts.terminalWinScore
                else -> 0.0
            }
        }

        // Evaluación heurística para estados no terminales
        return shipDiff +
            growthWeight * (myGrowth - oppGrowth) +
            transitWeight * (myTransitShips - oppTransitShips) +
            territoryScore
    }
    
    /**
     * takeAction: Simula el efecto de una acción del jugador sobre el estado
     * Proceso:
     * 1. Copia el estado actual (no modificamos el original)
     * 2. El oponente elige una acción aleatoria
     * 3. Aplica ambas acciones usando ForwardModel (motor del juego)
     * 4. Retorna el nuevo estado envuelto
     * 
     * Esto permite que MCTS explore diferentes futuros posibles.
     */
    override fun takeAction(action: Action): MCTSState {
        // TODO: Implementar simulación de acción en gameState
        // Esto requiere lógica de Game Engine para actualizar el estado

        val newState = gameState.deepCopy() // Crear una copia del estado actual
        val model = ForwardModel(newState, params) // Usar los parametros del juego recibidos por el agente
        val oponent = playerId.opponent()

        // Simular la acción
        val oponentAction = getActions(oponent).randomOrNull() ?: Action.doNothing() // Acción aleatoria del oponente
        
        model.step(mapOf(playerId to action, oponent to oponentAction)) // Simular un paso con ambas acciones


        return GameStateWrapper(newState, playerId, params, mcts) // Retornar nuevo estado envuelto
    }
    
    /**
     * takeRandomAction: Simula una acción aleatoria del juador actual
     * Usado durante la fase de simulación (rollout) del MCTS para llegar a términos rápidamente
     */
    override fun takeRandomAction(): MCTSState {
        val actions = getActions(playerId)
        if (actions.isEmpty()) return this
        val randomAction = actions.random()
        return takeAction(randomAction)
    }
    
    /**
     * getActions: Genera todas las acciones posibles para el jugador
     * Estrategia en dos fases:
     * 
     * FASE 1 - DEFENSA: 
     *   Si un planeta está en peligro (naves enemigas en tránsito > naves propias),
     *   genera acciones defensivas para envisar refuerzos desde planetas cercanos.
     * 
    * FASE 2 - ATAQUE:
    *   Desde cualquier planeta propio, genera acciones ofensivas
    *   incluso si también está siendo defendido
     * 
     * Límite: Como máximo MAX_ACTIONS_PER_STATE (24) acciones para no explotar búsqueda
     */
    override fun getActions(playerId: Player): List<Action> {
        // Filtrar planetas sin transportadores activos (disponibles para nuevas órdenes)
        val myPlanets = gameState.planets.filter { it.owner == playerId && it.transporter == null }
        val otherPlanets = gameState.planets.filter { it.owner != playerId }
        
        val actions = mutableListOf<Action>()
        
        // ========== FASE 1: DEFENSA ==========
        // Generar acciones defensivas para planetas bajo ataque
        val dangerPlanets = myPlanets.filter { isPlanetInDanger(it, playerId) }
        for (dangerPlanet in dangerPlanets) {
            val defenseActions = defendPlanet(dangerPlanet, playerId)
            actions.addAll(defenseActions)
        }
        
        // ========== FASE 2: OFENSA ==========
        for (source in myPlanets) {
            // Seleccionar solo los mejores objetivos para no explotar el árbol
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

        // Acción de defensa: siempre incluir "no hacer nada"
        if (actions.isEmpty()) {
            actions.add(Action.doNothing())
        }
        
        // Limitar acciones a MAX_ACTIONS_PER_STATE para no explotar el árbol de búsqueda
        if (actions.size > mcts.maxActionsPerState) {
            return actions.shuffled().take(mcts.maxActionsPerState)
        }

        return actions
    }

    /**
     * isAttackViable: Determina si vale la pena atacar un objetivo
     * 
     * Lógica: Un ataque es rentable si el daño que podemos hacer 
     * es mayor que la defensa que puede montar el objetivo mientras llegan nuestras naves.
     * 
     * Cálculo:
     *   - Naves a enviar: 50% del planeta origen
     *   - Defensa esperada: naves_actuales + (growthRate * distancia_en_ticks)
     *   - Viable si: naves_a_enviar > defensa_esperada
     * 
     * Esto evita desperdiciar recursos en objetivos muy protegidos o lejanos.
     */
    private fun isAttackViable(source: Planet, target: Planet): Boolean {
        val distance = source.position.distance(target.position)

        val shipsToSend = source.nShips * mcts.attackShipsFraction
        val expectedDefense = target.nShips + target.growthRate * distance

        return shipsToSend > expectedDefense
    }

    /**
     * targetScore: Calcula la prioridad de atacar un objetivo
     * 
     * Fórmula: (bonus_propiedad) * growthRate / (distancia + 1)
     * 
     * Factor de propiedad (qué tipo de planeta es):
     *   - 2.0 para planetas enemigos (MÁXIMA PRIORIDAD: nos quita territorio y naves)
     *   - 1.0 para planetas neutros (MEDIA: nos da territorio y crecimiento)
     *   - 0.1 para planetas propios (BAJA: poco beneficio, es solo consolidación)
     * 
     * Score favorece:
     *   - high growthRate: planetas más productivos generan más recursos por turno
     *   - proximidad: distancia pequeña = menos tiempo de viaje, llegan antes
     *   - enemigos: quitar recursos al rival es mejor que conquistar neutro
     */
    private fun targetScore(source: Planet, target: Planet, playerId: Player): Double {
        val distance = source.position.distance(target.position)

        val ownershipBonus = when (target.owner) {
            playerId.opponent() -> mcts.enemyTargetBonus    // Atacar enemigos es prioridad
            Player.Neutral -> mcts.neutralTargetBonus       // Luego neutrales
            else -> mcts.ownTargetBonus                     // Evitar atacar los propios
        }

        return ownershipBonus * target.growthRate / (distance + 1.0)   
    }

    /**
     * isPlanetInDanger: Detecta si un planeta está siendo atacado
     * 
     * Criterio: Un planeta está en peligro si la flota enemiga en tránsito
     * hacia él es más grande que la guarnición que lo defiende actualmente.
     * 
     * Esto permite agradecer defensa reactiva ante amenazas inminentes.
     */
    private fun isPlanetInDanger(source: Planet, playerId: Player): Boolean {
        // Buscar todos los transportadores enemigos dirigidos a este planeta
        val incomingEnemies = gameState.planets
            .filter { it.owner == playerId.opponent() }
            .mapNotNull { it.transporter }
            .filter { it.destinationIndex == source.id }

        // Sumar todas las naves en tránsito
        val incomingShips = incomingEnemies.sumOf { it.nShips }
        
        // En peligro si enemigos superan nuestra defensa local
        return incomingShips > source.nShips
    }

    /**
     * defendPlanet: Genera acciones defensivas para un planeta bajo ataque
     * 
     * Estrategia:
     *   1. Calcular el déficit de defensa: (naves_atacantes - naves_defensa_local)
     *   2. Buscar planetas aliados cercanos que tengan naves disponibles
     *   3. Enviar refuerzos priorizando cercanía
     *   4. Mantener mínimo 5 naves en cada planeta como defensa local
     * 
     * Ventajas:
     *   - Minimiza naves desperdiciadas en defensa excesiva
     *   - Protege localmente primero (refuerzos más cercanos = más rápidos)
     *   - Garantiza resiliencia del territorio
     */
    private fun defendPlanet(dangerPlanet: Planet, playerId: Player): List<Action> {
        val defenseActions = mutableListOf<Action>()
        
        // Calcular cuántas naves enemigas atacan este planeta
        val incomingThreats = gameState.planets
            .mapNotNull { it.transporter }
            .filter { it.owner == playerId.opponent() }
            .filter { it.destinationIndex == dangerPlanet.id }
            .sumOf { it.nShips }

        // Naves que necesitamos para alcanzar paridad con los atacantes
        val shipsNeeded = (incomingThreats - dangerPlanet.nShips).coerceAtLeast(1.0)

        // Buscar planetas defensores: ordenados por distancia (los más cercanos primero)
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
        val distance = source.position.distance(target.position)
        val shipsToSend = (source.nShips * mcts.attackShipsFraction).coerceAtLeast(1.0)
        val expectedDefense = target.nShips + target.growthRate * distance

        val viable = shipsToSend > expectedDefense

        // Permitir exploración incluso si no es viable
        if (!viable && Math.random() > 0.3) return null

        return Action(playerId, source.id, target.id, shipsToSend)
    }
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
}