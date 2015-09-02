package com.tmiyamon.mdicons

import org.gradle.api.Project

class Asset {
    final String name
    final IconSpec iconSpec

    static Asset newWithAssetTarget(AssetTarget target) {
        new Asset(target.name, IconSpec.newWithAssetTarget(target))
    }

    static Asset findOf(Project project, String assetName) {
        def assetTargets = project.userAssets as List<AssetTarget>
        newWithAssetTarget(assetTargets.find { it.name == assetName })
    }

    static List<Asset> allOf(Project project) {
        def assetTargets = project.userAssets as List<AssetTarget>
        assetTargets.collect { newWithAssetTarget(it) }
    }

    Asset(String name, IconSpec iconSpec) {
        this.name = name
        this.iconSpec = iconSpec
    }

    static class IconSpec {
        final List<String> densities
        final List<String> names
        final List<String> colors
        final List<String> sizes

        IconSpec(List<String> densities, List<String> names, List<String> colors, List<String> sizes) {
            this.densities = densities
            this.names = names
            this.colors = colors
            this.sizes = sizes
        }

        static IconSpec newWithAssetTarget(AssetTarget target) {
            new IconSpec(
                target.densities,
                target.names,
                target.colors,
                target.sizes
            )
        }
    }
}
