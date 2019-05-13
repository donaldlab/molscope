package edu.duke.cs.molscope.molecule


enum class Element(val number: Int, val symbol: String) {

	Hydrogen(1, "H"),
	Carbon(6, "C"),
	Nitrogen(7, "N"),
	Oxygen(8, "O");
	// TODO: are these atomic numbers right?
	// TODO: add more elements

	companion object {

		private val bySymbol =
			HashMap<String,Element>().apply {
				for (element in values()) {
					put(element.symbol.toLowerCase(), element)
				}
			}

		fun getOrNull(symbol: String): Element? =
			bySymbol[symbol.toLowerCase()]

		operator fun get(symbol: String) =
			getOrNull(symbol)
			?: throw NoSuchElementException("no element with symbol $symbol")

		// TODO: get by atomic number?
	}
}
