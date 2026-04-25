package games.planetwars.agents.choco

import java.util.Random
import games.planetwars.core.GameState
import games.planetwars.core.Player
import games.planetwars.core.GameParams
import games.planetwars.core.ForwardModel
import games.planetwars.agents.Action
import games.planetwars.agents.PlanetWarsPlayer


/**
 * Interfaz genérica para MCTS (Monte Carlo Tree Search)
 * Define las operaciones que cualquier estado del juego debe soportar para ser compatible con MCTS.
 * Permite que el árbol de búsqueda explore diferentes estados del juego de forma uniforme.
 */
interface MCTSState {
    /** Verifica si el juego ha terminado (terminal o victoria/derrota alcanzada) */
    fun isTerminal(): Boolean
    
    /** Evalúa la calidad de un estado para el jugador dado (heurística de evaluación) */
    fun getResult(playerId: Player): Double  
    
    /** Simula una acción concreta y retorna el estado resultante */
    fun takeAction(action: Action): MCTSState
    
    /** Simula una acción aleatoria (usada en la fase de rollout/simulación) */
    fun takeRandomAction(): MCTSState
    
    /** Genera todas las acciones posibles para el jugador desde este estado */
    fun getActions(playerId: Player): List<Action>

    fun stateQuickEval(playerId: Player): Double
}

// Esqueleto del código obtenido de: https://github.com/cucer-castellano/monte-carlo-tree-search.git

/**
 * MonteCarloTreeSearch: Motor de búsqueda de acciones óptimas
 * 
 * Algoritmo MCTS en 4 fases:
 * 1. SELECCIÓN: Navega por el árbol existente usando UCB1 (Upper Confidence Bound)
 * 2. EXPANSIÓN: Agrega un nodo hijo con una acción no probada
 * 3. SIMULACIÓN (Rollout): Desde el nuevo nodo, simula aleatoriamente hasta estado terminal
 * 4. BACKPROPAGACIÓN: Propaga el resultado hacia la raíz para actualizar estadísticas
 * 
 * Tras MAX_ITERATIONS o TIME_BUDGET_MS, retorna la acción con mejor ratio ganancia/intentos
 */
class MonteCarloTreeSearch(val mcts: MCTSParams = MCTSParams()) {

    /**
     * run: Ejecuta el algoritmo MCTS para encontrar la mejor acción
     * @param initialState: Estado actual del juego
     * @param playerId: Jugador para el que optimizamos
     * @return: La acción más prometedora encontrada en la búsqueda
     */
    fun run(initialState: GameStateWrapper, playerId: Player): Action {
        val root = MCTSNode(initialState, playerId)
        val deadlineNanos = System.nanoTime() + mcts.timeBudgetMs * 1_000_000L

        // Realizar iteraciones hasta alcanzar límite de tiempo o máximo de iteraciones
        for (i in 0 until mcts.maxIterations) {
            if (System.nanoTime() >= deadlineNanos) break

            var node = root
            var tempState: MCTSState = initialState

            // === FASE 1: SELECCIÓN + EXPANSIÓN ===
            // Navega por el árbol hasta una hoja, o expande con una acción no probada
            while (!tempState.isTerminal()) {
                if (node.hasUntriedActions()) {
                    // Hay acciones no exploradas: crear nuevo nodo hijo
                    val action = node.getUntriedAction()
                    tempState = tempState.takeAction(action)
                    node = node.addChild(tempState, action)
                    break  // Salir para proceder a simulación desde este nodo nuevo
                } else {
                    // Todas las acciones exploradas: seleccionar hijo usando UCB1
                    node = node.selectChild(mcts)  // Balanceo explotación/exploración
                    tempState = node.state
                }
            }

            // === FASE 2: SIMULACIÓN (Rollout) ===
            // Simular acciones aleatorias hasta terminal o profundidad máxima
            var rolloutDepth = 0
            while (!tempState.isTerminal() && rolloutDepth < mcts.maxRolloutDepth) {
                tempState = tempState.takeRandomAction()
                rolloutDepth++
            }

            // === FASE 3: BACKPROPAGACIÓN ===
            // Propagar resultado desde hoja hacia raíz, actualizando estadísticas
            val result = tempState.getResult(playerId)
            node.backpropagate(result)
        }

        // === SELECCIÓN FINAL ===
        // Retornar la acción más visitada (mejor promedio empírico)
        return if (root.children.isNotEmpty()) {
            root.getBestChild().action ?: Action.doNothing()
        } else {
            Action.doNothing()  // Sin acciones exploradas: hacer nada
        }
    }
}

/**
 * MCTSNode: Nodo del árbol de búsqueda MCTS
 * 
 * Representa un estado del juego y mantiene estadísticas sobre su valor.
 * Cada nodo almacena:
 * - state: El estado del juego en este nodo
 * - playerId: Jugador que optimizamos
 * - parent: Referencia al nodo padre (para backpropagación)
 * - children: Lista de nodos hijos (acciones ya exploradas)
 * - untriedActions: Acciones pendientes de explorar desde este estado
 * - wins/visits: Estadísticas para calcular valor medio (wins/visits)
 */
class MCTSNode(val state: MCTSState, val playerId: Player, val parent: MCTSNode? = null) {
    
    /** Lista de nodos hijos (estados futuros explorados) */
    public val children = mutableListOf<MCTSNode>()
    
    /** Acciones que aún no hemos explorado desde este nodo */
    private val untriedActions = state.getActions(playerId).toMutableList()
    
    /** Suma acumulada de resultados (para calcular promedio) */
    private var wins = 0.0
    
    /** Número de veces que se visitó este nodo */
    private var visits = 0
    
    /** La acción que llevó a este estado desde su padre */
    var action: Action? = null

    /**
     * addChild: Crea y agrega un nodo hijo
     * @param state: Nuevo estado del juego tras la acción
     * @param action: Acción que lleva a este nuevo estado
     * @return: El nuevo nodo hijo creado
     */
    fun addChild(state: MCTSState, action: Action): MCTSNode {
        val child = MCTSNode(state, playerId, parent = this)
        child.action = action
        children.add(child)
        untriedActions.remove(action)  // Marcar acción como explorada
        return child
    }

    /**
     * backpropagate: Propaga el resultado desde este nodo hacia la raíz
     * Actualiza visits y wins en todos los ancestros
     * @param result: Valor de retorno del rollout (heurística de evaluación)
     */
    fun backpropagate(result: Double) {
        var node: MCTSNode? = this

        while (node != null) {
            node.visits++      // Incrementar visitas de este nodo
            node.wins += result  // Acumular valor de resultado
            node = node.parent  // Continuar hacia arriba
        }
    }

    /** true si aún existen acciones no exploradas desde este nodo */
    fun hasUntriedActions(): Boolean = untriedActions.isNotEmpty()
    
    /**
     * getUntriedAction: Extrae aleatoriamente una acción no probada
     * @return: Una acción seleccionada al azar de las no exploradas
     */
    fun getUntriedAction(): Action =  untriedActions.removeAt(Random().nextInt(untriedActions.size))
    
    /**
     * getBestChild: Retorna el hijo más visitado
     * Se usa para seleccionar la acción final a retornar (mayor confianza empírica)
     * @return: El nodo hijo con mayor número de visitas
     */
    fun getBestChild(): MCTSNode {
        return children.maxByOrNull { it.visits }!!
    }
    
    /**
     * selectChild: Selecciona hijo usando UCB1 (Upper Confidence Bound)
     * Balanceo entre explotación (mejor promedio) y exploración (menos visitados)
     * Fórmula: exploitation + exploration
     *   - exploitation = wins/visits (promedio de resultados)
     *   - exploration = sqrt(2*ln(parentVisits)/childVisits) (bonus a no visitados)
     * @return: El hijo que maximiza UCB1
     */
    fun selectChild(params: MCTSParams): MCTSNode {
    val parentVisits = visits.coerceAtLeast(1)

    val wrapper = state as? GameStateWrapper

    val progress = if (wrapper != null && wrapper.params.maxTicks > 0) {
        wrapper.gameState.gameTick.toDouble() / wrapper.params.maxTicks
    } else 0.5

    val myPlayer = playerId
    val opponent = playerId.opponent()

    // Evaluación rápida (lightweight)
    val myScore = state.stateQuickEval(myPlayer)
    val oppScore = state.stateQuickEval(opponent)

    val leadFactor = (myScore - oppScore).coerceIn(-1.0, 1.0)

    // === UCT dinámico ===
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
