<fold text='h1: # Simage Plugins'># Si<fold text='...'>mage Plugins

The repository contains sources code of SimagePlugins as well as demo maya project.

<!-- TOC depthFrom:2 </fold>-->

[W<fold text='...'>hat is this repository for](#what-is-this-repository-for)

<fold text='unordered list: - [What is this reposi...k-to)'>- [What is this repository for](#what-is-this-repository-for)
<fold text='- [How do I get set up...bles)'>- [How do I get set up](#how-do-i-get-set-up)
    - [Requirements](#requirements)
    - [Environment Variables](#environment-variables)</fold>
<fold text='- [Plugin List](#plugi...rmer)'>- [Plugin List](#plugin-list)
    - [MxBezier3scalar](#mxbezier3scalar)
    - [PushDeformer](#pushdeformer)
    - [NoiseDeformer](#noisedeformer)
    - [SmoothDeformer](#smoothdeformer)</fold>
- [Who do I talk to](#who-do-i-talk-to)</fold>

<!-- /TOC </fold>-->

<fold text='h2: ## What is this repository for'>## What is this repository for

<fold text='unordered list: - Maya plugins for Sim... beta'>- Maya plugins for Simage Animation & Media Limited
- Version: 0.0.1 beta</fold></fold>

<fold text='h2: ## How do I get set up'>## How do I get set up

You need to install Maya 2017 as well as Visual Studio 2017 first. After that, setup your [environment variables](#enviroment-variables). It is essential because Visual Studio need these variables to be able to compile successfully. The deliverable is called "SimagePlugins.mll" which contains several plugins (please check out [Plugin List](#plugin-list) below for more details).

<fold text='h3: ### Requirements'>### Requirements

<fold text='unordered list: - Maya 2017
- Visual S... 2017'>- Maya 2017
- Visual Studio 2017</fold></fold>

<fold text='h3: ### Environment Variables'>### Environment Variables

<fold text='table: Name | Description | E...g-ins'>Name | Description | Example
---------|----------|---------
 MAYA_PATH | your install directory of Maya 2017 | C:\Program Files\Autodesk\Maya2017
 MAYA_PLUGIN_PATH | the desitnation folder of Maya plugin |  %USERPROFILE%\Documents\maya\2017\plug-ins</fold></fold></fold>

<fold text='h2: ## Plugin List'>## Plugin List

<fold text='h3: ### MxBezier3scalar'>### MxBezier3scalar

<fold text='unordered list: - A simple node that t...3.mb"'>- A simple node that take a float number (w) as input and generates 3 float numbers (p1, p2, p3) as outputs. You can use this node together with maya's build-in blend shape by driving 3 different blend-shapes' weight with these three outputs. The math is based on Bezier-3 with slight modification in order to make p2 = 1, p1 = 0, p3 = 0 when w = 0.5.
- please see "./MayaProject/scenes/MxBezier3.mb"</fold>

<fold text='table: Parameters | Short Nam...value'>Parameters | Short Name | In/Out | Type | Default Value | Description
---|---|---|---|---|---
 W  | w | In | float | 0 | driving value (from 0 to 1)
 P1 | p1 | Out | float | NA | first component's value
 P2 | p2 | Out | float | NA | second component's value
 P3 | p3 | Out | float | NA | third component's value</fold>

- MEL example

<fold text='code fence: ```mel

//create a Sim...

```'>```mel

//create a Simage_MxBezier3scalar node and name it "SMB3_1"
createNode "Simage_MxBezier3scalar" -n "SMB3_1"

```</fold></fold>

<fold text='h3: ### PushDeformer'>### PushDeformer

<fold text='unordered list: - Push vertex in its n...r.mb"'>- Push vertex in its normal direction.
- please see "./MayaProject/scenes/PushDeformer.mb"</fold>

<fold text='table: Parameters | Short Nam...plier'>Parameters | Short Name | In/Out | Type | Default Value | Description
---|---|---|---|---|---
 amplitude | amp | In | float | 1 | displacement length
 multiplier | mult | In | float | 1 | displacement multiplier</fold>

- MEL example

<fold text='code fence: ```mel

//select a geo...

```'>```mel

//select a geometry first and then run the following script
deformer -type "Simage_PushDeformer"

```</fold></fold>

<fold text='h3: ### NoiseDeformer'>### NoiseDeformer

<fold text='unordered list: - Perform Perlin Noise...r.mb"'>- Perform Perlin Noise deform on geometry.
- please see "./MayaProject/scenes/NoiseDeformer.mb"</fold>

<fold text='table: Parameters | Short Nam...rator'>Parameters | Short Name | In/Out | Type | Default Value | Description
---|---|---|---|---|---
 amplitude | amp | In | float | 1 | the displacement strength
 scale | sc | In | float | 0.01 | noise frequency (the larger the value, the higher the frequency)
 displace X | disx | In | float | 0 | noise displacement in X axis
 displace Y | disy | In | float | 0 | noise displacement in Y axis
 displace Z | disz | In | float | 0 | noise displacement in Z axis
 octave | oct | In | integer | 1 | noise complexity (at least 1)
 seed | seed | In | integer | 123456 | seed for random generator</fold>

- MEL example

<fold text='code fence: ```mel

//select a geo...

```'>```mel

//select a geometry first and then run the following script
deformer -type "Simage_NoiseDeformer"

```</fold></fold>

<fold text='h3: ### SmoothDeformer'>### SmoothDeformer

- Perform smooth deform on geometry.

<fold text='table: Parameters | Short Nam...st 1)'>Parameters | Short Name | In/Out | Type | Default Value | Description
---|---|---|---|---|---
 weight by distance | wbd | In | boolean | false | smooth algorithm will consider the distance between adjacent vertex if this value is set to true
 multiplier | mult | In | float | 1 | the effectiveness of each smoothing step (from 0 to 1)
 iteration | iter | In | integer | 1 | number of smoothing steps (at least 1)</fold>

- MEL example

<fold text='code fence: ```mel

//select a geo...

```'>```mel

//select a geometry first and then run the following script
deformer -type "Simage_SmoothDeformer"

```</fold></fold></fold>

<fold text='h2: ## Who do I talk to'>## Who do I talk to

<fold text='unordered list: - Max Tong: maxtong198...e.hk>'>- Max Tong: maxtong198776@gmail.com

- Simage Animation and Media Limited: <https://www.simage.hk></fold></fold></fold>