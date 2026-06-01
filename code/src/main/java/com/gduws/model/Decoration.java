package com.gduws.model;

/**
 * 地块装饰物。纯表现层覆盖物（树木、灌木、岩石等），不影响通行与战斗
 *
 * <p>由地图文件的 {@code [decoration]} 层逐格指定，渲染时叠加在地形纹理之上</p>
 */
public enum Decoration {
    FLOWER,  // 花朵
    TREE,    // 针叶树
    TREE2,   // 针叶树（变体）
    BUSH,    // 灌木
    ROCK     // 岩石
}
