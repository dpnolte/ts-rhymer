## TypeScript Rhymer - kotlin rhymes with typescript (pun intended)
This is a kotlin annotation processor to generate TypeScript definitions. This is useful for javascript based apps to bridge  data structures from kotlin to JS. Heavily inspired by (standing on the shoulders of giants ;-): 
- [moshi-code-gen](https://medium.com/@sweers/exploring-moshis-kotlin-code-gen-dec09d72de5e)
- [tsGenerator](https://github.com/ntrrgc/ts-generator/tree/master/src/main/kotlin/me/ntrrgc/tsGenerator)

### Usage
1. Enable kapt as explained [here](https://kotlinlang.org/docs/reference/kapt.html)

2. Add jitpack as repository:
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```
3. Add dependencies:
```
dependencies {
    compileOnly 'com.github.dpnolte.ts-rhymer:annotation:0.1'
    kapt 'com.github.dpnolte.ts-rhymer:codegen:0.1'
}
```
4. Provide kapt arguments (optional):
```
kapt {
    arguments {
        arg("typescript.module", "Types") // becomes: declare module "Types"
        arg("typescript.outputDir", "/Users/derk/awesomeproject") // where the file will be saved
        arg("typescript.indent", "  ") // indentation (defaults to 2 spaces)
    }
}
```

5. Decorate your (root) classes with @TypeScript:
```
@TypeScript
class KotlinClass { ... }
```

## simple example
```
enum class Visibility { VISIBLE, INVISIBLE, GONE}

open class ViewOptions  {
    var visibility: Visibility = Visibility.VISIBLE
}

open class ViewGroupOptions : ViewOptions() {
    var layoutWidth: Int? = null
    var layoutHeight: Int? = null
    var clipChildren: Boolean? = null
    var clipToPadding: Boolean? = null
    var layoutAnimation: AnimationOptions? = null
}


class MarginLayoutOptions : ViewGroupOptions() {
    var layoutMarginLeft: Int? = null
    var layoutMarginTop: Int? = null
    var layoutMarginRight: Int? = null
    var layoutMarginBottom: Int? = null
}
```
typescript:
```
/* generated @ 2018-08-16T13:05:06.372 */
declare module "NativeTypes" {
  interface AnimationOptions {
    count?: number;
    index?: number;
  }

  interface MarginLayoutOptions extends ViewGroupOptions {
    layoutMarginBottom?: number;
    layoutMarginLeft?: number;
    layoutMarginRight?: number;
    layoutMarginTop?: number;
  }

  interface ViewGroupOptions extends ViewOptions {
    clipChildren?: boolean;
    clipToPadding?: boolean;
    layoutAnimation?: AnimationOptions;
    layoutHeight?: number;
    layoutWidth?: number;
  }

  interface ViewOptions {
    visibility: Visibility;
  }

  enum Visibility { 'VISIBLE', 'INVISIBLE', 'GONE' }
}
```

### inheritance
```
@TypeScript
class GrandFather: Parent() {
    val wildest: Boolean = true
}
open class Parent (var mildlyWild: Boolean = true) : GrandSon()
open class GrandSon ( val amIWild: Boolean = true ) Four(4)
}
```
```
/* generated @ 2018-08-16T13:19:17.996 */
declare module "NativeTypes" {
  interface GrandFather extends Parent {
    wildest: boolean;
  }

  interface GrandSon {
    amIWild: boolean;
  }

  interface Parent extends GrandSon {
    mildlyWild: boolean;
  }
}

```
### type variables
```
@TypeScript
class TypedClassWithAWildcard<T : List<ParameterTypeClass>, L, Q: ParameterTypeClass>(
        var test: T,
        val type1: Q,
        val type2: L,
        val type3: HashMap<String, AnimationOptions>,
        val wildCardType4: HashMap<String, HashMap<Int, *>>)
open class ParameterTypeClass (val data: String = "")
open class AnimationOptions (var count: Int? = null, var index: Int? = null)
```
```
/* generated @ 2018-08-16T13:09:33.210 */
declare module "NativeTypes" {
  interface AnimationOptions {
    count?: number;
    index?: number;
  }

  interface ParameterTypeClass {
    data: string;
  }

  interface TypedClassWithAWildcard<Q extends ParameterTypeClass, T extends Array<ParameterTypeClass>, L> {
    complexType4: { [key: string]: { [key: number]: any } };
    test: T;
    type1: Q;
    type2: L;
    type3: { [key: string]: AnimationOptions };
  }
}
```

### collection types:
```
open class AnimationOptions (var count: Int? = null, var index: Int? = null)
open class BaseTypeClass (val data: String = "", val yes: String = "no")
@TypeScript
class CollectionsClass<TMapVaule : BaseTypeClass>(
        val animationOptions: List<AnimationOptions>,
        var intArray: IntArray,
        val complexType: HashMap<String, List<String>>,
        val map: HashMap<String, TMapVaule>,
        val stringList: List<String>,
        val koe: Set<String>, val paartje: Pair<String, Int>
)
```
```
/* generated @ 2018-08-16T13:13:46.846 */
declare module "NativeTypes" {
  interface AnimationOptions {
    count?: number;
    index?: number;
  }

  interface BaseTypeClass {
    data: string;
    yes: string;
  }

  interface CollectionsClass<TMapVaule extends BaseTypeClass> {
    animationOptions: Array<AnimationOptions>;
    complexType: { [key: string]: Array<string> };
    intArray: Array<number>;
    koe: Set<string>;
    map: { [key: string]: TMapVaule };
    paartje: [string, number];
    stringList: Array<string>;
  }
}
```
Note that wild card will be converted to any

### Motivation
Android is more constrained as a platform. The annotation processor creates the typescript defintions at compile so that no overhead is added. The already existing generators were using reflection, which - on android- is a performance and a dex count hit. 
