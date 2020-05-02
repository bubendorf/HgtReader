# SRTM/DEM HgtReader

A small plug-in for the OpenStreetMap Osmosis Tool to read and convert SRTM/DEM .hgt file into contour lines.

## Plugin Usage

- Activate the plugin with the Osmosis parameter ‘--hgtfile-reader’, or short ‘--rhgt’.
- Use the following optional parameters to configure the process of contour lines creation:

|**Option**|**Description**|**Valid Values**|**Default Value**|
|----------|---------------|----------------|-----------------|
|`file`|path to the input .hgt file|N28E086.hgt<br/>(e.g.)||
|`interval`|interval between contour values|Integer|20|
|`elev-key`*|tag of OSM Way to carry associated elevation value (e.g. 20,40,60,80,...)|String|ele|
|`contour-key`*|tag of OSM Way to mark as contour line|String|contour|
|`contour-val`*|value of `contour-key`|String|elevation|
|`contour-ext-key`*|tag of OSM Way to carry contour ext-info|String|contour_ext|
|`contour-ext-major`*|value of `contour-ext-key`, means the contour line is in 500 interval|String|elevation_major|
|`contour-ext-medium`*|value of `contour-ext-key`, means the contour line is in 100 interval|String|elevation_medium|
|`contour-ext-minor`*|value of `contour-ext-key`, means the contour line is in other intervals|String|elevation_minor|
|`oversampling`|use this oversampling|Integer|1|
|`elevation-factor`|value of `elevation-factor`, means the contour line is in other intervals|Integer|1|
|`simplify-contours-epsilon`|Simplify the line using the Ramer-Douglas-Peucker algorithm using the value [meters]. 0 means no simplification|Double|0.0|
|`major`|Interval for major elevation|Integer|500|
|`medium`|Interval fpr medium elevation|Integer|100|
|`max-nodes-per-way`|Maximum number of nodes per way. 0 means no limit|Integer|0|

[*] In favor of [Mapsforge](https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md). Need to match with the configuration in tag-mapping.xml.

### Example

- Convert a SRTM .hgt into Binary-PBF format:<br/>`$ osmosis --rhgt file=N28E086.hgt --wb file=N28E086.pbf`<br/>

## Plugin Installation

- Download the latest version plugin (**jar-with-dependencies**) from [releases](https://github.com/plben/HgtReader/releases).
- Create the directory "$USER_HOME/.openstreetmap/osmosis/plugins/" if not exist.
- Copy the downloaded HgtReader-x.x.x-jar-with-dependencies.jar to this directory.
