package edu.duke.cs.molscope.molecule

import edu.duke.cs.molscope.SharedSpec
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.kotlintest.matchers.types.shouldNotBeSameInstanceAs
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow


class TestMoleculeCombine : SharedSpec({

	test("one small") {

		val mol = Molecule("small")
		val srcC = Atom(Element.Carbon, "C", 1.0, 2.0, 3.0).also { mol.atoms.add(it) }
		val srcN = Atom(Element.Nitrogen, "N", 4.0, 5.0, 6.0).also { mol.atoms.add(it) }
		mol.bonds.add(srcC, srcN)

		val (combined, atomMap) = listOf(mol).combine("combined")

		combined.atoms.size shouldBe 2
		val dstC = combined.atoms.find { it.name == "C" }!!
		val dstN = combined.atoms.find { it.name == "N" }!!
		srcC shouldNotBeSameInstanceAs dstC
		srcN shouldNotBeSameInstanceAs dstN

		combined.bonds.count() shouldBe 1
		combined.bonds.isBonded(dstC, dstN) shouldBe true

		atomMap[srcC] shouldBeSameInstanceAs dstC
		atomMap[srcN] shouldBeSameInstanceAs dstN
	}

	test("two small") {

		val mol1 = Molecule("small1")
		val srcC1 = Atom(Element.Carbon, "C", 1.0, 2.0, 3.0).also { mol1.atoms.add(it) }
		val srcN1 = Atom(Element.Nitrogen, "N", 4.0, 5.0, 6.0).also { mol1.atoms.add(it) }
		mol1.bonds.add(srcC1, srcN1)

		val mol2 = Molecule("small2")
		val srcC2 = Atom(Element.Carbon, "C", 11.0, 12.0, 13.0).also { mol2.atoms.add(it) }
		val srcN2 = Atom(Element.Nitrogen, "N", 14.0, 15.0, 16.0).also { mol2.atoms.add(it) }
		mol2.bonds.add(srcC2, srcN2)

		val (combined, atomMap) = listOf(mol1, mol2).combine("combined")

		combined.atoms.size shouldBe 4
		val dstC1 = combined.atoms.find { it.name == "C" && it.pos.x == 1.0 }!!
		val dstN1 = combined.atoms.find { it.name == "N" && it.pos.x == 4.0 }!!
		val dstC2 = combined.atoms.find { it.name == "C" && it.pos.x == 11.0 }!!
		val dstN2 = combined.atoms.find { it.name == "N" && it.pos.x == 14.0 }!!
		srcC1 shouldNotBeSameInstanceAs dstC1
		srcN1 shouldNotBeSameInstanceAs dstN1
		srcC2 shouldNotBeSameInstanceAs dstC2
		srcN2 shouldNotBeSameInstanceAs dstN2

		combined.bonds.count() shouldBe 2
		combined.bonds.isBonded(dstC1, dstN1) shouldBe true
		combined.bonds.isBonded(dstC2, dstN2) shouldBe true

		atomMap[srcC1] shouldBeSameInstanceAs dstC1
		atomMap[srcN1] shouldBeSameInstanceAs dstN1
		atomMap[srcC2] shouldBeSameInstanceAs dstC2
		atomMap[srcN2] shouldBeSameInstanceAs dstN2
	}

	test("one polymer") {

		val mol = Polymer("poly")
		val srcC = Atom(Element.Carbon, "C", 1.0, 2.0, 3.0).also { mol.atoms.add(it) }
		val srcN = Atom(Element.Nitrogen, "N", 4.0, 5.0, 6.0).also { mol.atoms.add(it) }
		mol.bonds.add(srcC, srcN)
		val srcChain = Polymer.Chain("A").also { mol.chains.add(it) }
		val srcRes = Polymer.Residue("1", "RES", listOf(srcC, srcN)).also { srcChain.residues.add(it) }

		val (combined, atomMap) = listOf(mol).combine("combined")

		combined as Polymer

		combined.atoms.size shouldBe 2
		val dstC = combined.atoms.find { it.name == "C" }!!
		val dstN = combined.atoms.find { it.name == "N" }!!
		srcC shouldNotBeSameInstanceAs dstC
		srcN shouldNotBeSameInstanceAs dstN

		combined.bonds.count() shouldBe 1
		combined.bonds.isBonded(dstC, dstN) shouldBe true

		combined.chains.size shouldBe 1
		val dstChain = combined.chains.find { it.id == "A" }!!
		val dstRes = dstChain.residues.find { it.id == "1" }!!
		srcChain shouldNotBeSameInstanceAs dstChain
		srcRes shouldNotBeSameInstanceAs dstRes
		dstRes.atoms shouldContainExactlyInAnyOrder  listOf(dstC, dstN)

		atomMap[srcC] shouldBeSameInstanceAs dstC
		atomMap[srcN] shouldBeSameInstanceAs dstN
	}

	test("two polymer") {

		val mol1 = Polymer("poly1")
		val srcC1 = Atom(Element.Carbon, "C", 1.0, 2.0, 3.0).also { mol1.atoms.add(it) }
		val srcN1 = Atom(Element.Nitrogen, "N", 4.0, 5.0, 6.0).also { mol1.atoms.add(it) }
		mol1.bonds.add(srcC1, srcN1)
		val srcChain1 = Polymer.Chain("A").also { mol1.chains.add(it) }
		val srcRes1 = Polymer.Residue("1", "RES", listOf(srcC1, srcN1)).also { srcChain1.residues.add(it) }

		val mol2 = Polymer("poly1")
		val srcC2 = Atom(Element.Carbon, "C", 11.0, 12.0, 13.0).also { mol2.atoms.add(it) }
		val srcN2 = Atom(Element.Nitrogen, "N", 14.0, 15.0, 16.0).also { mol2.atoms.add(it) }
		mol2.bonds.add(srcC2, srcN2)
		val srcChain2 = Polymer.Chain("B").also { mol2.chains.add(it) }
		val srcRes2 = Polymer.Residue("1", "RES", listOf(srcC2, srcN2)).also { srcChain2.residues.add(it) }

		val (combined, atomMap) = listOf(mol1, mol2).combine("combined")

		combined as Polymer

		combined.atoms.size shouldBe 4
		val dstC1 = combined.atoms.find { it.name == "C" && it.pos.x == 1.0 }!!
		val dstN1 = combined.atoms.find { it.name == "N" && it.pos.x == 4.0 }!!
		val dstC2 = combined.atoms.find { it.name == "C" && it.pos.x == 11.0 }!!
		val dstN2 = combined.atoms.find { it.name == "N" && it.pos.x == 14.0 }!!
		srcC1 shouldNotBeSameInstanceAs dstC1
		srcN1 shouldNotBeSameInstanceAs dstN1
		srcC2 shouldNotBeSameInstanceAs dstC2
		srcN2 shouldNotBeSameInstanceAs dstN2

		combined.bonds.count() shouldBe 2
		combined.bonds.isBonded(dstC1, dstN1) shouldBe true
		combined.bonds.isBonded(dstC2, dstN2) shouldBe true

		combined.chains.size shouldBe 2
		val dstChain1 = combined.chains.find { it.id == "A" }!!
		val dstRes1 = dstChain1.residues.find { it.id == "1" }!!
		val dstChain2 = combined.chains.find { it.id == "B" }!!
		val dstRes2 = dstChain2.residues.find { it.id == "1" }!!
		srcChain1 shouldNotBeSameInstanceAs dstChain1
		srcRes1 shouldNotBeSameInstanceAs dstRes1
		dstRes1.atoms shouldContainExactlyInAnyOrder listOf(dstC1, dstN1)
		srcChain2 shouldNotBeSameInstanceAs dstChain2
		srcRes2 shouldNotBeSameInstanceAs dstRes2
		dstRes2.atoms shouldContainExactlyInAnyOrder listOf(dstC2, dstN2)

		atomMap[srcC1] shouldBeSameInstanceAs dstC1
		atomMap[srcN1] shouldBeSameInstanceAs dstN1
		atomMap[srcC2] shouldBeSameInstanceAs dstC2
		atomMap[srcN2] shouldBeSameInstanceAs dstN2
	}

	test("two polymer, chain id collision") {

		val mol1 = Polymer("poly1")
		val srcC1 = Atom(Element.Carbon, "C", 1.0, 2.0, 3.0).also { mol1.atoms.add(it) }
		val srcN1 = Atom(Element.Nitrogen, "N", 4.0, 5.0, 6.0).also { mol1.atoms.add(it) }
		mol1.bonds.add(srcC1, srcN1)
		val srcChain1 = Polymer.Chain("A").also { mol1.chains.add(it) }
		val srcRes1 = Polymer.Residue("1", "RES", listOf(srcC1, srcN1)).also { srcChain1.residues.add(it) }

		val mol2 = Polymer("poly1")
		val srcC2 = Atom(Element.Carbon, "C", 11.0, 12.0, 13.0).also { mol2.atoms.add(it) }
		val srcN2 = Atom(Element.Nitrogen, "N", 14.0, 15.0, 16.0).also { mol2.atoms.add(it) }
		mol2.bonds.add(srcC2, srcN2)
		val srcChain2 = Polymer.Chain("A").also { mol2.chains.add(it) }
		val srcRes2 = Polymer.Residue("1", "RES", listOf(srcC2, srcN2)).also { srcChain2.residues.add(it) }

		shouldThrow<IllegalArgumentException> {
			listOf(mol1, mol2).combine("combined")
		}
	}

	test("two polymer, chain id resolve") {

		val mol1 = Polymer("poly1")
		val srcC1 = Atom(Element.Carbon, "C", 1.0, 2.0, 3.0).also { mol1.atoms.add(it) }
		val srcN1 = Atom(Element.Nitrogen, "N", 4.0, 5.0, 6.0).also { mol1.atoms.add(it) }
		mol1.bonds.add(srcC1, srcN1)
		val srcChain1 = Polymer.Chain("A").also { mol1.chains.add(it) }
		val srcRes1 = Polymer.Residue("1", "RES", listOf(srcC1, srcN1)).also { srcChain1.residues.add(it) }

		val mol2 = Polymer("poly1")
		val srcC2 = Atom(Element.Carbon, "C", 11.0, 12.0, 13.0).also { mol2.atoms.add(it) }
		val srcN2 = Atom(Element.Nitrogen, "N", 14.0, 15.0, 16.0).also { mol2.atoms.add(it) }
		mol2.bonds.add(srcC2, srcN2)
		val srcChain2 = Polymer.Chain("A").also { mol2.chains.add(it) }
		val srcRes2 = Polymer.Residue("1", "RES", listOf(srcC2, srcN2)).also { srcChain2.residues.add(it) }

		val (combined, atomMap) = listOf(mol1, mol2).combine("combined", resolveChainIds = true)

		(combined as Polymer).chains.map { it.id } shouldContainExactly listOf("A", "B")
	}
})
