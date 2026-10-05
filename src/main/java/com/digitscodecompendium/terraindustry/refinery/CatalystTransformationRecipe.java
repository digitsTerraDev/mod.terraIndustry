package com.digitscodecompendium.terraindustry.refinery;

import java.util.List;
import java.util.Objects;

/** One nearby input block may be transformed by each catalyst activation. */
public record CatalystTransformationRecipe(BlockStateSpec inputBlock, List<Outcome> outputs) {
    public CatalystTransformationRecipe {
        Objects.requireNonNull(inputBlock, "inputBlock");
        outputs = List.copyOf(outputs);
        if (outputs.isEmpty()) throw new IllegalArgumentException("A catalyst recipe needs an output");
        double totalChance = outputs.stream().mapToDouble(Outcome::chance).sum();
        if (totalChance > 1.0D + 1.0E-8D) throw new IllegalArgumentException("Catalyst output chances cannot exceed 1");
    }
    public CatalystTransformationRecipe(String inputBlock, Outcome... outputs) {
        this(BlockStateSpec.parse(inputBlock), List.of(outputs));
    }
    public record Outcome(BlockStateSpec outputBlock, double chance) {
        public Outcome {
            Objects.requireNonNull(outputBlock, "outputBlock");
            if (chance <= 0.0D || chance > 1.0D) throw new IllegalArgumentException("Chance must be in (0, 1]");
        }
        public Outcome(String outputBlock, double chance) { this(BlockStateSpec.parse(outputBlock), chance); }
    }
}
