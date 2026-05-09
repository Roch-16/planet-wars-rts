package games.planetwars.agents.choco

import games.planetwars.core.GameState
import games.planetwars.core.Player
import games.planetwars.core.GameParams
import games.planetwars.core.ForwardModel
import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer


/**
 * Interfaz mínima que un estado debe implementar para poder participar en MCTS.
 *
 * El árbol solo depende de estas operaciones para expandir, simular y evaluar
 * estados sin acoplarse al motor concreto del juego.
 */
interface MCTSState {
    /** Indica si no deben explorarse más acciones desde este estado. */
    fun isTerminal(): Boolean
    
    /** Devuelve una evaluación del estado desde la perspectiva del jugador indicado. */
    fun getResult(playerId: Player): Double  
    
    /** Aplica una acción concreta y retorna el estado resultante. */
    fun takeAction(action: Action): MCTSState
    
    /** Avanza el estado con una acción aleatoria para la fase de rollout. */
    fun takeRandomAction(): MCTSState
    
    /** Genera las acciones disponibles para el jugador indicado. */
    fun getActions(playerId: Player): List<Action>

    /** Evaluación rápida heurística para ajustar la exploración durante la selección. */
    /** propia implementación */
    fun stateQuickEval(playerId: Player): Double
}

// Esqueleto del código obtenido de: https://github.com/cucer-castellano/monte-carlo-tree-search.git

/**
 * Motor de búsqueda MCTS para seleccionar acciones en Planet Wars.
 *
 * El ciclo sigue las fases clásicas:
 * 1. Selección: desciende por el árbol usando UCT dinámico.
 * 2. Expansión: crea un hijo con una acción aún no explorada.
 * 3. Rollout: simula jugadas aleatorias hasta terminar o agotar profundidad.
 * 4. Backpropagation: propaga el resultado hacia la raíz.
 *
 * Al final devuelve la acción del hijo más visitado en la raíz.
 */
class MonteCarloTreeSearch(val mcts: MCTSParams = MCTSParams()) {

    /** Ejecuta MCTS sobre el estado inicial y devuelve la acción recomendada. */
    fun run(initialState: GameStateWrapper, playerId: Player): Action {
        val root = MCTSNode(initialState, playerId)
        val deadlineNanos = System.nanoTime() + mcts.timeBudgetMs * 1_000_000L

        // Realizar iteraciones hasta alcanzar límite de tiempo o máximo de iteraciones
        for (i in 0 until mcts.maxIterations) {
            if (System.nanoTime() >= deadlineNanos) break

            var node = root
            var tempState: MCTSState = initialState

            // Selección + expansión: baja por el árbol hasta encontrar un nodo expandable.
            while (!tempState.isTerminal()) {
                if (node.hasUntriedActions()) {
                    // Todavía quedan acciones sin explorar: expandir un nuevo hijo.
                    val action = node.getUntriedAction()
                    tempState = tempState.takeAction(action)
                    node = node.addChild(tempState, action)
                    break
                } else {
                    // Todas las acciones exploradas: seguir con UCT dinámico.
                    node = node.selectChild(mcts)
                    tempState = node.state
                }
            }

            // Rollout: simular acciones aleatorias hasta terminal o profundidad límite.
            var rolloutDepth = 0
            while (!tempState.isTerminal() && rolloutDepth < mcts.maxRolloutDepth) {
                tempState = tempState.takeRandomAction()
                rolloutDepth++
            }

            // Backpropagation: actualizar estadísticas desde la hoja hasta la raíz.
            val result = tempState.getResult(playerId)
            node.backpropagate(result)
        }

        // Selección final: usar el hijo más visitado como acción recomendada.
        return if (root.children.isNotEmpty()) {
            root.getBestChild().action ?: Action.doNothing()
        } else {
            Action.doNothing()
        }
    }
}

/**
 * Nodo del árbol MCTS.
 *
 * Guarda el estado, la acción que lo generó y las estadísticas acumuladas
 * para calcular la preferencia de cada rama durante la búsqueda.
 */
class MCTSNode(val state: MCTSState, val playerId: Player, val parent: MCTSNode? = null) {
    
    /** Hijos ya explorados desde este nodo. */
    public val children = mutableListOf<MCTSNode>()
    
    /** Acciones que todavía no se han expandido desde este nodo. */
    private val untriedActions = state.getActions(playerId).toMutableList()
    
    /** Suma acumulada de resultados de las simulaciones. */
    private var wins = 0.0
    
    /** Número de visitas acumuladas. */
    private var visits = 0
    
    /** Acción que llevó al estado actual desde el padre. */
    var action: Action? = null

    /** Crea y agrega un nodo hijo para el estado alcanzado por una acción. */
    fun addChild(state: MCTSState, action: Action): MCTSNode {
        val child = MCTSNode(state, playerId, parent = this)
        child.action = action
        children.add(child)
        untriedActions.remove(action)
        return child
    }

    /** Propaga el resultado de un rollout hasta la raíz. */
    fun backpropagate(result: Double) {
        var node: MCTSNode? = this

        while (node != null) {
            node.visits++
            node.wins += result
            node = node.parent
        }
    }

    /** True si todavía quedan acciones por explorar. */
    fun hasUntriedActions(): Boolean = untriedActions.isNotEmpty()
    
    /** Extrae la siguiente acción aún no expandida, priorizando la mejor puntuada. */
    fun getUntriedAction(): Action = untriedActions.removeAt(0)
    
    /** Retorna el hijo con más visitas, usado como decisión final en la raíz. */
    fun getBestChild(): MCTSNode {
        return children.maxByOrNull { it.visits }!!
    }
    
    /**
     * Selecciona el mejor hijo usando UCT con exploración dinámica.
     *
     * La constante de exploración se ajusta según el progreso de la partida
     * y la ventaja heurística estimada entre ambos jugadores.
     */
    fun selectChild(params: MCTSParams): MCTSNode {
    val parentVisits = visits.coerceAtLeast(1)

    val wrapper = state as? GameStateWrapper

    val progress = if (wrapper != null && wrapper.params.maxTicks > 0) {
        wrapper.gameState.gameTick.toDouble() / wrapper.params.maxTicks
    } else 0.5

    val myPlayer = playerId
    val opponent = playerId.opponent()

    // Evaluación rápida para ajustar la exploración de forma dinámica.
    val myScore = state.stateQuickEval(myPlayer)
    val oppScore = state.stateQuickEval(opponent)

    val leadFactor = (myScore - oppScore).coerceIn(-1.0, 1.0)

    // UCT dinámico.
    val dynamicC = (
        params.explorationConstant *
        (1.0 - progress) *
        (1.0 - 0.5 * leadFactor)
    ).coerceIn(0.1, 2.5)

    return children.maxByOrNull { child ->
        if (child.visits == 0) {
            Double.POSITIVE_INFINITY
        } else {
            val exploitation = child.wins / child.visits
            val exploration = Math.sqrt(
                Math.log(parentVisits.toDouble()) / child.visits
            )
            exploitation + dynamicC * exploration
        }
    }!!
}
}
