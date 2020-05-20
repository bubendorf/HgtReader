# SRTM/DEM HgtReader

A small plug-in for the OpenStreetMap Osmosis Tool to read and convert SRTM/DEM .hgt file into contour lines.

## Plugin Usage

- Activate the plugin with the Osmosis parameter ‘--hgtfile-reader’, or short ‘--rhgt’.
- Use the following optional parameters to configure the process of contour lines creation:

|**Option**|**Description**|**Valid Values**|**Default Value**|
|----------|---------------|----------------|-----------------|
|`file`|path to the input .hgt file|N28E086.hgt<br/>(e.g.)||
|`interval`|interval between contour values|Integer|20|
|`levels`|Comma separated list of levels to use instead of the interval.|String||
|`elev-key`*|tag of OSM Way to carry associated elevation value (e.g. 20,40,60,80,...)|String|ele|
|`contour-key`*|tag of OSM Way to mark as contour line|String|contour|
|`contour-val`*|value of `contour-key`|String|elevation|
|`contour-ext-key`*|tag of OSM Way to carry contour ext-info.|String|contour_ext|
|`contour-ext-major`*|value of `contour-ext-key`, means the contour line is in 500 interval.|String|elevation_major|
|`contour-ext-medium`*|value of `contour-ext-key`, means the contour line is in 100 interval.|String|elevation_medium|
|`contour-ext-minor`*|value of `contour-ext-key`, means the contour line is in other intervals.|String|elevation_minor|
|`oversampling`|use this oversampling. Recommended value: 2|Double|1|
|`elevation-factor`|Enhance the accuracy by this factor. Recommended value: 1000|Integer|1|
|`elevation-offset`|Add this to every internal elevation value|Integer|0|
|`simplify-contours-epsilon`|Simplify the line using the Ramen-Douglas-Peucker algorithm. 0 means no simplification. Recommended value: 2 [meter]|Double|0.0|
|`major`|Interval for major elevation|Integer|500|
|`medium`|Interval for medium elevation|Integer|100|
|`max-nodes-per-way`|Maximum number of nodes per way. 0 means no limit. Garmin units have a limit of 2000 nodes per way. Recommended value: 1000|Integer|0|
|`min-nodes-per-way`|Minimum number of nodes per closed way. 0 means no limit. Ways with less nodes gets ignored.|Integer|4|
|`min-nodes-per-open-way`|Minimum number of nodes per open way. 0 means no limit. Ways with less nodes gets ignored.|Integer|12|
|`write-contour-ways`|Write the contour itself to the output pipe.|Boolean|True|
|`write-hgt-nodes`|Write every entry of the HGT file as node to the output pipe.|Boolean|False|
|`write-raster-nodes`|Write the oversampled points as nodes to the output pipe.|Boolean|False|
|`round-to-garmin-map-unit`|Round the coordinates to 360°/2^24 = 0.00002146°. This is the internal precision Garmin units use.|Boolean|False|

[*] In favor of [Mapsforge](https://github.com/mapsforge/mapsforge/blob/master/docs/Getting-Started-Map-Writer.md). Need to match with the configuration in tag-mapping.xml.

### Example

- Convert a SRTM .hgt into Binary-PBF format:<br/>`$ osmosis --rhgt file=N28E086.hgt --wb file=N28E086.pbf`<br/>

## Plugin Installation

- Download the latest version plugin (**jar-with-dependencies**) from [releases](https://github.com/plben/HgtReader/releases).
- Create the directory "$USER_HOME/.openstreetmap/osmosis/plugins/" if not exist.
- Copy the downloaded HgtReader-x.x.x-jar-with-dependencies.jar to this directory.
