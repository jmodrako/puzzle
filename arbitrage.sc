import $ivy.`com.lihaoyi::upickle:1.4.1 compat`
import $ivy.`com.lihaoyi::requests:0.6.9 compat`

import scala.collection.mutable.ListBuffer
import scala.math.BigDecimal.RoundingMode

type RatesMatrix = Array[Array[BigDecimal]]
type Currency = String
type Rate = BigDecimal
type ExchangePairs = Map[String, Rate]

case class Arbitrage(
  currencyChain: Seq[Currency] // This should be actually a NonEmptyList.
) {

  def calculateProfit(
    initialMoney: BigDecimal,
    exchangePairs: ExchangePairs
  ): BigDecimal = {
    val arbitragePairs = currencyChain
      .sliding(2, 1)
      .map(_.mkString("_"))

    arbitragePairs
      .foldLeft(initialMoney) { (moneyAcc, exchangePairKey) =>
        moneyAcc * exchangePairs(exchangePairKey)
      }
  }

  override def toString: String = currencyChain.mkString(" -> ")
}

case class Edge(
  from: Int,
  to: Int,
  cost: BigDecimal
)

object bellmanford {

  val InvalidVertexIndex = -1

  def prepareEdges(
    exchangePairs: ExchangePairs,
    currencies: Seq[Currency]
  ): Seq[Edge] =
    for {
      (currFrom, currFromIndex) <- currencies.zipWithIndex
      (currTo, currToIndex) <- currencies.zipWithIndex
    } yield {
      val exchangeKey = s"${currFrom}_$currTo"
      val exchangeRate = exchangePairs(exchangeKey)

      Edge(
        from = currFromIndex,
        to = currToIndex,
        // -log(...) is used because an algo is based on summing and minimizing edges
        // but our profit is calculated based on multiplication of edges (rates, profit -> > 1)
        // so simple trick is done here: log(ab) = log(a) + log(b)
        // minus sign is to change the direction of inequality > 1 (multiplicative case) to < 1 (additive case)
        cost = -math.log(exchangeRate.doubleValue)
      )
    }

  def findNegativeCycleWithIndices(
    verticesCount: Int,
    startVertexIndex: Int,
    edges: Seq[Edge]
  ): Option[Seq[Int]] = {
    val distances = Array.fill(verticesCount)(BigDecimal(Double.MaxValue))
    val predecessors = Array.fill(verticesCount)(-1)

    distances(startVertexIndex) = 0

    var lastRelaxedVertex = InvalidVertexIndex
    for (_ <- 0 until verticesCount) {
      lastRelaxedVertex = InvalidVertexIndex

      for (edge <- edges) {
        val candidateDistance = distances(edge.from) + edge.cost

        if (distances(edge.to) > candidateDistance) {
          distances(edge.to) = distances(edge.from) + edge.cost
          predecessors(edge.to) = edge.from
          lastRelaxedVertex = edge.to
        }
      }
    }

    // If after last iteration there was a change -> negative cycle detected.
    // So we have to reconstruct path to be able to calculate arbitrage.
    if (lastRelaxedVertex == InvalidVertexIndex) { None }
    else {
      val negativeCycle = reverseTraceForNegativeCycle(lastRelaxedVertex, verticesCount, predecessors)
      Some(negativeCycle)
    }
  }

  private def reverseTraceForNegativeCycle(
    lastRelaxedVertex: Int,
    verticesCount: Int,
    predecessors: Array[Int]
  ): Seq[Int] = {
    var initialVertex = lastRelaxedVertex

    for (_ <- 0 until verticesCount) initialVertex = predecessors(initialVertex)

    val arbitragePath = ListBuffer[Int]()
    var currentPathVertex = initialVertex
    var loop = true

    while (loop) {
      arbitragePath.addOne(currentPathVertex)

      // stop loop if we reached initial vertex again
      if (currentPathVertex == initialVertex && arbitragePath.length >= 2) {
        loop = false
      }

      currentPathVertex = predecessors(currentPathVertex)
    }

    arbitragePath.toSeq
  }
}

object arbitrage {

  def findArbitrage(
    edges: Seq[Edge],
    currencies: Seq[Currency]
  ): Option[Arbitrage] =
    bellmanford
      .findNegativeCycleWithIndices(
        verticesCount = currencies.length,
        startVertexIndex = 0,
        edges = edges
      )
      .map(_.map(currencies))
      .map(Arbitrage)
}

object util {

  def downloadExchangePairs(): (ExchangePairs, Seq[Currency]) = {
    val request = requests.get("https://fx.priceonomics.com/v1/rates/")
    val exchangePairs = ujson.read(request.data.array).obj.view.mapValues(v => BigDecimal(v.str)).toMap
    val currenciesList = exchangePairs.keys.map(_.split("_")(0)).toSeq

    (exchangePairs, currenciesList)
  }

  def printArbitragesInfo(
    potentialArbitrage: Option[Arbitrage],
    exchangePairs: ExchangePairs
  ): Unit = potentialArbitrage match {
    case None => println("No arbitrage found...")
    case Some(arbitrage) =>
      val initialMoney = BigDecimal(5)
      val initialCurrency = arbitrage.currencyChain.head // safe because arbitrage is not empty

      println(s"Given arbitrage chain: $arbitrage")
      println(s"And initial amount of $initialMoney $initialCurrency")

      println(s"When arbitrage is calculated")
      val earningsFromArbitrage = arbitrage.calculateProfit(initialMoney, exchangePairs)

      val profitInCurrency = earningsFromArbitrage - initialMoney
      val profitInPercent = ((profitInCurrency / initialMoney) * 100).setScale(2, RoundingMode.UP)

      println(s"Then you profit: $profitInCurrency $initialCurrency  ($profitInPercent%)\n")
  }
}

// -------------- Start point of the program --------------

// Download and prepare data.
val (exchangePairs, currencies) = util.downloadExchangePairs()

// Run Bellman-Ford algorithm and try to find first arbitrage.
val potentialArbitrage = arbitrage.findArbitrage(
  edges = bellmanford.prepareEdges(
    exchangePairs = exchangePairs,
    currencies = currencies
  ),
  currencies = currencies
)

// Final info to the user.
util.printArbitragesInfo(potentialArbitrage, exchangePairs)
