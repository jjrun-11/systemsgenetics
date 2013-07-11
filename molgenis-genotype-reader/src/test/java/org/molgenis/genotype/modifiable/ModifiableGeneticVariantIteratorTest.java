package org.molgenis.genotype.modifiable;

import static org.testng.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import org.molgenis.genotype.variant.GeneticVariant;
import org.molgenis.genotype.variant.ReadOnlyGeneticVariant;
import org.testng.annotations.Test;

public class ModifiableGeneticVariantIteratorTest
{

	@Test
	public void createModifiableGeneticVariantIterable()
	{
		GeneticVariant variant1 = ReadOnlyGeneticVariant.createSnp("Rs1", 1, "chr1", null, 'A', 'T');
		GeneticVariant variant2 = ReadOnlyGeneticVariant.createSnp("Rs2", 20, "chr1", null, 'G', 'C');
		ArrayList<GeneticVariant> variants = new ArrayList<GeneticVariant>(2);
		variants.add(variant1);
		variants.add(variant2);

		HashSet<GeneticVariant> excludeList = new HashSet<GeneticVariant>();

		Iterable<ModifiableGeneticVariant> modifiableVariants = ModifiableGeneticVariantIterator
				.createModifiableGeneticVariantIterable(variants.iterator(), null, excludeList);

		Iterator<ModifiableGeneticVariant> modifiableVariantsIterator = modifiableVariants.iterator();

		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant1);
		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant2);

		assertEquals(modifiableVariantsIterator.hasNext(), false);

	}

	@Test
	public void createModifiableGeneticVariantIterableFiltered()
	{
		GeneticVariant variant1 = ReadOnlyGeneticVariant.createSnp("Rs1", 1, "chr1", null, 'A', 'T');
		GeneticVariant variant2 = ReadOnlyGeneticVariant.createSnp("Rs2", 20, "chr1", null, 'G', 'C');
		ArrayList<GeneticVariant> variants = new ArrayList<GeneticVariant>(2);
		variants.add(variant1);
		variants.add(variant2);

		HashSet<GeneticVariant> excludeList = new HashSet<GeneticVariant>();
		excludeList.add(variant1);

		Iterable<ModifiableGeneticVariant> modifiableVariants = ModifiableGeneticVariantIterator
				.createModifiableGeneticVariantIterable(variants.iterator(), null, excludeList);

		Iterator<ModifiableGeneticVariant> modifiableVariantsIterator = modifiableVariants.iterator();

		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant2);

		assertEquals(modifiableVariantsIterator.hasNext(), false);

	}

	@Test
	public void createModifiableGeneticVariantIterableFiltered2()
	{
		GeneticVariant variant1 = ReadOnlyGeneticVariant.createSnp("Rs1", 1, "chr1", null, 'A', 'T');
		GeneticVariant variant2 = ReadOnlyGeneticVariant.createSnp("Rs2", 20, "chr1", null, 'G', 'C');
		ArrayList<GeneticVariant> variants = new ArrayList<GeneticVariant>(2);
		variants.add(variant1);
		variants.add(variant2);

		HashSet<GeneticVariant> excludeList = new HashSet<GeneticVariant>();
		excludeList.add(variant2);

		Iterable<ModifiableGeneticVariant> modifiableVariants = ModifiableGeneticVariantIterator
				.createModifiableGeneticVariantIterable(variants.iterator(), null, excludeList);

		Iterator<ModifiableGeneticVariant> modifiableVariantsIterator = modifiableVariants.iterator();

		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant1);

		assertEquals(modifiableVariantsIterator.hasNext(), false);

	}

	@Test
	public void createModifiableGeneticVariantIterableFiltered3()
	{
		GeneticVariant variant1 = ReadOnlyGeneticVariant.createSnp("Rs1", 1, "chr1", null, 'A', 'T');
		GeneticVariant variant2 = ReadOnlyGeneticVariant.createSnp("Rs2", 20, "chr1", null, 'G', 'C');
		GeneticVariant variant3 = ReadOnlyGeneticVariant.createSnp("Rs3", 30, "chr1", null, 'G', 'C');
		GeneticVariant variant4 = ReadOnlyGeneticVariant.createSnp("Rs4", 40, "chr1", null, 'G', 'C');
		GeneticVariant variant5 = ReadOnlyGeneticVariant.createSnp("Rs5", 50, "chr1", null, 'G', 'C');

		ArrayList<GeneticVariant> variants = new ArrayList<GeneticVariant>(2);
		variants.add(variant1);
		variants.add(variant2);
		variants.add(variant3);
		variants.add(variant4);
		variants.add(variant5);

		HashSet<GeneticVariant> excludeList = new HashSet<GeneticVariant>();
		excludeList.add(variant2);
		excludeList.add(variant3);

		Iterable<ModifiableGeneticVariant> modifiableVariants = ModifiableGeneticVariantIterator
				.createModifiableGeneticVariantIterable(variants.iterator(), null, excludeList);

		Iterator<ModifiableGeneticVariant> modifiableVariantsIterator = modifiableVariants.iterator();

		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant1);
		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant4);
		assertEquals(modifiableVariantsIterator.next().getOriginalVariant(), variant5);

		assertEquals(modifiableVariantsIterator.hasNext(), false);

	}

}
