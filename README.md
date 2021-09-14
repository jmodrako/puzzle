# Arbitrage Puzzle

## Repo Overview
This project aims to show possible arbitrage opportunities in a set of connected currencies. 
Exchange rates are pulled from `https://fx.priceonomics.com/v1/rates/` REST API via GET request.

## Implementation details
To find possible arbitrage in a set of exchange rates one requires an algorithm from graph theory that can
work on directed, weightened graphs with negative cycles (at least to report such fact). One of such algorithms 
is 'Bellman-Ford', which has been chosen in this solution. The complexity of this implementation is `O(n*m)`
where `n` is number of currencies (vertices), and `m` is number of all exchange pairs (edges).

First phase of the solution is the relaxation of the edges. The second one is to retrieve a 
path that made an arbitrage.

Because of the Bellman-Ford usage, the final arbitrage will be the first one in the graph as this algorithm
can't be applied to find ALL possible arbitrages.

## Running code
Solution has been made as a Scala script called from Ammonite tool: `amm arbitrage.sc`.
After script does its job, the info will be printed:

* message that no arbitrages has been found
* message about found arbitrage with some test case scenario:

```
Given arbitrage chain: JPY -> BTC -> JPY
And initial amount of 5 JPY
When arbitrage is applied
Then you profit: 0.85077518964760 JPY  (17.02%)
```

## Possible Improvements
Current implementation is using simple, not improved version of an algorithm. There are some tricks to
avoid unnecessary relaxation when nothing changed in the edges (so we can save time in for loops).

Another idea could be to implement different algorithm, for example Floyd-Warshall one (O(n^3)).
There are also algorithms that are about finding k-length cycles in the graphs.