/*
 * $Id:  TranslatorTools.java 19:03:26 wrzodek $
 * $URL: TranslatorTools.java $
 * ---------------------------------------------------------------------
 * This file is part of KEGGtranslator, a program to convert KGML files
 * from the KEGG database into various other formats, e.g., SBML, GML,
 * GraphML, and many more. Please visit the project homepage at
 * <http://www.cogsys.cs.uni-tuebingen.de/software/KEGGtranslator> to
 * obtain the latest version of KEGGtranslator.
 *
 * Copyright (C) 2011 by the University of Tuebingen, Germany.
 *
 * KEGGtranslator is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation. A copy of the license
 * agreement is provided in the file named "LICENSE.txt" included with
 * this software distribution and also available online as
 * <http://www.gnu.org/licenses/lgpl-3.0-standalone.html>.
 * ---------------------------------------------------------------------
 */
package de.zbit.kegg;

import java.awt.Color;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import y.base.DataMap;
import y.base.Edge;
import y.base.EdgeMap;
import y.base.Graph;
import y.base.Node;
import y.base.NodeMap;
import y.layout.organic.SmartOrganicLayouter;
import y.view.Graph2D;
import y.view.Graph2DLayoutExecutor;
import y.view.LineType;
import y.view.NodeRealizer;
import y.view.Selections;
import de.zbit.kegg.ext.GenericDataMap;
import de.zbit.kegg.ext.GraphMLmaps;
import de.zbit.kegg.gui.TranslatorPanel;
import de.zbit.kegg.io.KEGG2yGraph;

/**
 * This class is intended to provide various translator tools.
 * 
 * @author Clemens Wrzodek
 * @version $Rev$
 */
public class TranslatorTools {
  public static final transient Logger log = Logger.getLogger(TranslatorTools.class.getName());
  
  /**
   * A graph on which operations are performed.
   */
  private Graph2D graph;
  
  /**
   * A reverse map of {@link Graph#getDataProvider(Object)} where Object is
   * {@link KEGG2yGraph#mapDescription}.
   * <p>In other words, returns directly the map for, e.g. "entrezIds"
   */
  private Map<String, DataMap> descriptor2Map=null;
  
  /**
   * Static String for the <code>type</code> {@link NodeMap} of the {@link #graph}
   * to be used for, e.g. microRNAs.
   */
  public final static String RNA_TYPE = "RNA";
  
  
  public TranslatorTools(TranslatorPanel tp){
    this(tp.isGraphML()?(Graph2D) tp.getDocument():null);
  }
  
  public TranslatorTools(Graph2D graph){
    super();
    this.graph=graph;
    if (this.graph==null) log.warning("Graph is null!");
    init();
  }
  
  @SuppressWarnings("unchecked")
  private void init() {
    if (graph==null) {
      descriptor2Map=null;
      return;
    }
    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    descriptor2Map = mapDescriptionMap.createReverseMap();
  }
  
  
  /**
   * Highlight all given GeneIDs in RED color. And selects these nodes.
   * @param graph translated pathway with annotated geneIDs
   * @param ncbiGeneIDs geneIDs to color in Red.
   */
  public void highlightGenes(Iterable<Integer> ncbiGeneIDs) {
    highlightGenes(ncbiGeneIDs, Color.RED, Color.LIGHT_GRAY, true);
  }
  
  public void highlightGenes(Iterable<Integer> ncbiGeneIDs, Color highlightColor, Color forAllOthers, boolean changeSelection) {
    if (forAllOthers!=null) {
      setColorOfAllNodesExceptPathwayReferences(forAllOthers);
    }
    if (changeSelection) graph.unselectAll();
    Map<Integer, List<Node>> id2node = getGeneID2NodeMap();
    for (Integer integer : ncbiGeneIDs) {
      List<Node> nList = id2node.get(integer);
      if (nList!=null) {
        for (Node node : nList) {
          graph.getRealizer(node).setFillColor(highlightColor);
          if (changeSelection) {
            graph.getRealizer(node).setSelected(true);
          }
        }
      } else {
        log.info("Could not get a Node for " + integer);
      }
    }
  }
  
  /**
   * Set a unique {@link Color} to all nodes, that are no pathway references.
   * @param colorForUnaffectedNodes
   */
  public void setColorOfAllNodesExceptPathwayReferences(Color colorForUnaffectedNodes) {
    // Set unaffected color for all other nodes but reference nodes.
    for (Node n: graph.getNodeArray()) {
      if (TranslatorTools.getKeggIDs(n).toLowerCase().trim().startsWith("path:")) continue;
      graph.getRealizer(n).setFillColor(colorForUnaffectedNodes);
    }
  }
  
  /**
   * Draws the frame and caption of all nodes, containing the given
   * string in the nodeLabel tag red. Blackens all other frames and
   * labels.
   * @param containedString
   */
  public void searchGenes(String containedString) {
    // TODO: instead of globally resetting Border color and thickness,
    // remember highlighted nodes in graph and remove highlighting
    // prior to a new search.
    // Also try to somehow remember old color and thickness and restore
    // bzw. set thickness to math.max([2], currentThickness).
    containedString = containedString.toLowerCase();
    for (Node n: graph.getNodeArray()) {
      Color color = Color.BLACK;
      LineType lt = LineType.LINE_1;
      // Check actual label and "all names" (label map).
      if (graph.getLabelText(n).toLowerCase().contains(containedString) ||
          getNodeInfoIDs(n, GraphMLmaps.NODE_LABEL).toLowerCase().contains(containedString)) {
        color = Color.RED;
        lt = LineType.LINE_2;
      }
      graph.getRealizer(n).setLineColor(color);
      graph.getRealizer(n).setLineType(lt);
      graph.getRealizer(n).getLabel().setTextColor(color);
    }
  }
  
  /**
   * @param descriptor e.g., "keggIds" or "entrezIds".
   * <p>See {@link GraphMLmaps} for a complete list.
   * @return map for the descriptor.
   */
  public DataMap getMap(String descriptor) {
    return descriptor2Map.get(descriptor);
  }
  
  /**
   * Get all maps registered in the graph.
   * @param descriptor
   * @return
   */
  public Collection<DataMap> getMaps() {
    return descriptor2Map.values();
  }
  
  /**
   * Get all descriptors of all maps registered
   * in the graph.
   * @return
   */
  public Set<String> getMapDescriptors() {
    return descriptor2Map.keySet();
  }
  
  /**
   * Get a reverse map for the <code>descriptor</code>. This means,
   * descriptor object to List of nodes with this descriptor.
   * <p>Key is an Object, Value is a <pre>List<Node></pre>
   * @param descriptor
   * @return
   */
  public Map<Object, List<Node>> getReverseMap(String descriptor) {
    DataMap dm = getMap(descriptor);
    if (dm==null) return null;
    
    Map<Object, List<Node>> revMap = new HashMap<Object, List<Node>>();
    Node[] arr = graph.getNodeArray();
    for (Node n: arr) {
      Object o = dm.get(n);
      if (o==null) continue;
      List<Node> list = revMap.get(o);
      if (list==null) {
        list = new LinkedList<Node>();
        revMap.put(o, list);
      }
      list.add(n);
    }
    
    return revMap;
  }
  
  /**
   * @return a map from nodeLabel (microRNA_name Uppercased and trimmed) to the actual node.
   */
  @Deprecated
  public Map<String, List<Node>> getRNA2NodeMap() {

    // Build a map from GeneID 2 Node
    Map<String, List<Node>> mi2node = new HashMap<String, List<Node>>();

    NodeMap typeMap = (NodeMap) getMap(GraphMLmaps.NODE_TYPE);
    NodeMap labelMap = (NodeMap) getMap(GraphMLmaps.NODE_LABEL);
    if (typeMap==null || labelMap==null) {
      log.severe(String.format("Could not find %s %s mapping.", (typeMap==null?"type":""), (labelMap==null?"label":"")));
      return mi2node; // return an empty map.
    }
    
    // build the resulting map
    for (Node n : graph.getNodeArray()) {
      Object type = typeMap.get(n);
      if (type!=null && type.equals(RNA_TYPE)) {
        Object label = labelMap.get(n);
        if (label==null) continue;
        String key = label.toString().toUpperCase().trim();
        
        // Get list, associated with node label
        List<Node> list = mi2node.get(key);
        if (list==null) {
          list = new LinkedList<Node>();
          mi2node.put(key, list);
        }
        
        // Add node to list.
        list.add(n);
        
      }
    }
    
    return mi2node;
  }
  
  /**
   * @return true if and only if the underlying graph contains any nodes
   * of type {@link #RNA_TYPE}.
   */
  public boolean containsRNAnodes() {

    // Get Type map
    NodeMap typeMap = (NodeMap) getMap(GraphMLmaps.NODE_TYPE);
    if (typeMap==null) {
      log.severe(String.format("Could not find %s mapping.", (typeMap==null?"type":"")));
      return false;
    }
    
    // Check all nodes
    for (Node n : graph.getNodeArray()) {
      Object type = typeMap.get(n);
      if (type!=null && type.equals(RNA_TYPE)) {
        return true;
      }
    }
    
    return false;
  }

  /**
   * Return a map from Entrez GeneID to corresponding {@link Node} for the given
   * translated pathway.
   * @param graph
   * @return map from geneID to List of nodes.
   */
  public Map<Integer, List<Node>> getGeneID2NodeMap() {
    // Build a map from GeneID 2 Node
    Map<Integer, List<Node>> id2node = new HashMap<Integer, List<Node>>();
    
    // Get the NodeMap from entrez 2 node.
//    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    NodeMap entrez = (NodeMap) descriptor2Map.get(GraphMLmaps.NODE_GENE_ID); //null;
//    if (mapDescriptionMap==null) return null;
//    for (int i=0; i<graph.getRegisteredNodeMaps().length; i++) {
//      NodeMap nm = graph.getRegisteredNodeMaps()[i];
//      if (mapDescriptionMap.getV(nm).equals(GraphMLmaps.NODE_GENE_ID)) {
//        entrez = nm;
//        break;
//      }
//    }
    if (entrez==null) {
      log.severe("Could not find Node2EntrezID mapping.");
      return null;
    }
    
    // build the resulting map
    for (Node n : graph.getNodeArray()) {
      Object entrezIds = entrez.get(n);
      if (entrezIds!=null && entrezIds.toString().length()>0) {
        String[] ids = entrezIds.toString().split(",|\\s"); // comma or space separated.
        for (String id: ids) {
          if (id==null || id.trim().length()<1) continue;
          try {
            // Get Node collection for gene ID
            Integer intId = Integer.parseInt(id);
            List<Node> list = id2node.get(intId);
            if (list==null) {
              list = new LinkedList<Node>();
              id2node.put(intId, list);
            }
            // Add node to list.
            list.add(n);
          } catch (NumberFormatException e) {
            log.log(Level.WARNING, "Could not get geneID for node.", e);
          }
        }
      }
    }
    
    return id2node;
  }
  
  /**
   * @param n a node
   * @param descriptor e.g., "keggIds" or "entrezIds". See {@link GraphMLmaps} for a complete list.
   * @return the string associated with this node.
   */
  @SuppressWarnings("unchecked")
  public static String getNodeInfoIDs(Node n, String descriptor) {
    if (n==null || n.getGraph()==null) return null;
    Graph graph = n.getGraph();
    
    // Get the NodeMap from kegg 2 node.
    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    NodeMap nodeMap = null;
    if (mapDescriptionMap==null) return null;
    for (int i=0; i<graph.getRegisteredNodeMaps().length; i++) {
      NodeMap nm = graph.getRegisteredNodeMaps()[i];
      if (mapDescriptionMap.getV(nm).equals(descriptor)) {
        nodeMap = nm;
        break;
      }
    }
    if (nodeMap==null) {
      log.severe(String.format("Could not find Node to %s mapping.", (descriptor==null?"null":descriptor)));
      return null;
    }
    
    // return kegg id(s)
    Object id = nodeMap.get(n);
    return id!=null?id.toString():null;
  }
  
  /**
   * Actually, a faster and more generic version of {@link #getNodeInfoIDs(Node, String)}.
   * @param node_or_edge and {@link Node} or {@link Edge}
   * @param descriptor a descriptor for a {@link NodeMap} or {@link EdgeMap} in the current
   * {@link #graph}.
   * @return the requested information or null, if not available.
   */
  public Object getInfo(Object node_or_edge, String descriptor) {
    // Get the NodeMap from kegg 2 node.
    DataMap nodeMap = descriptor2Map.get(descriptor);
    if (nodeMap==null) {
      // This method is used without checking if the map has ever been set
      // before. So this warning is really more for debugging purposes.
      log.fine(String.format("Could not find Node to %s mapping.", (descriptor==null?"null":descriptor)));
      return null;
    }
    
    // return kegg id(s)
    Object id = nodeMap.get(node_or_edge);
    return id!=null?id:null;
  }
  
  /**
   * Set information to a map, contained in the current {@link #graph}. If the map given by
   * <code>descriptor</code> does not exist, it will be created.
   * @param node_or_edge any node or edge
   * @param descriptor descriptor of the map (e.g. "entrezIds", see {@link GraphMLmaps})
   * @param value value to set.
   */
  public void setInfo(Object node_or_edge, String descriptor, Object value) {
    // Get the NodeMap for the descriptor.
    DataMap nodeMap = getMap(descriptor);
    if (nodeMap==null && value==null) return; // all ok. Unset in a non-existing map.
    
    if (nodeMap==null) {
      // Create non-existing map automatically
      nodeMap = createMap(descriptor, (node_or_edge instanceof Node) );
      log.fine(String.format("Created not existing Node to %s mapping.", (descriptor==null?"null":descriptor)));
    }
    
    // set / unset Value
    nodeMap.set(node_or_edge, value);
  }
  
  /**
   * Ensures that a specific map exists in this graph. Simply returns
   * the map, if it already existed, else, the map will be created as
   * an empty map.
   * @param descriptor
   * @param isNodeMap false, if and only if this should be an
   * {@link EdgeMap}. If true, a {@link NodeMap} will be created.
   * @return the map.
   */
  public DataMap ensureMapExists(String descriptor, boolean isNodeMap) {
    // Get the NodeMap for the descriptor.
    DataMap nodeMap = getMap(descriptor);
    
    if (nodeMap==null) {
      // Create non-existing map automatically
      nodeMap = createMap(descriptor, isNodeMap );
      log.fine(String.format("Created not existing Node to %s mapping.", (descriptor==null?"null":descriptor)));
    }
    return nodeMap;
  }
  
  
  /**
   * Create a new Node- or EdgeMap in the {@link #graph}.
   * @param descriptor descriptor for the map.
   * @param nodeMap if true, a {@link NodeMap} will be created. Else,
   * a {@link EdgeMap} will be created.
   * @return the created map.
   */
  private DataMap createMap(String descriptor, boolean nodeMap) {
    DataMap map;
    if (nodeMap) {
      map = graph.createNodeMap();
    } else {
      map = graph.createEdgeMap();
    }
    
    addMap(descriptor, map);
    
    return map;
  }

  /**
   * Registers a map WITHIN THESE TOOLS and linked to the {@link GenericDataMap}
   * <code>mapDescriptionMap</code>. Does not touch the graph itself!
   * 
   * @param descriptor
   * @param map
   */
  @SuppressWarnings("unchecked")
  public void addMap(String descriptor, DataMap map) {
    // Add info about map also to descriptors.
    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    mapDescriptionMap.set(map, descriptor);
    descriptor2Map.put(descriptor, map);
  }

  /**
   * Returns the organism kegg abbreviation from a graph.
   * @param graph
   * @return e.g. "ko" or "hsa",...
   */
  public static String getOrganismKeggAbbrFromGraph(Graph2D graph) {
    
    for (Node n: graph.getNodeArray()) {
      String id = getKeggIDs(n).toLowerCase().trim();
      if (id.contains(":")) {
        return id.substring(0, id.indexOf(':'));
      }
    }
    
    return null;
  }

  /**
   * @param n
   * @return kegg ids, separated by a "," for the given node.
   */
  public static String getKeggIDs(Node n) {
    return getNodeInfoIDs(n, GraphMLmaps.NODE_KEGG_ID);
  }

  /**
   * Returns the node, referring to a certain pathway in the graph.
   * Can be used to get the title node, by simply submitted the 
   * pathwayID of the current graph as argument.
   * @param graph
   * @param pathwayID
   * @return
   */
  public static Node getTitleNode(Graph2D graph, String pathwayID) {
    pathwayID = pathwayID.toLowerCase().trim();
    for (Node n: graph.getNodeArray()) {
      String id = getKeggIDs(n).toLowerCase().trim();
      if (id.startsWith("path:") && id.contains(pathwayID)) {
        return n;
      }
    }
    
    return null;
  }

  /**
   * @return the currently underlying {@link Graph2D} of this tools instance.
   */
  public Graph2D getGraph() {
    return graph;
  }


  /**
   * Layout the freshly added nodes.
   * @param newNodes nodes to layout
   */
  public void layoutNodeSubset(Set<Node> newNodes) {
    layoutNodeSubset(newNodes, false);
  }
  public void layoutNodeSubset(Set<Node> newNodes, boolean strict) {
    if (newNodes==null || newNodes.size()<1) return;
    
    // Create a selection map that contains all new nodes.
    NodeMap dp = Selections.createSelectionNodeMap(graph);
    for (Node n : graph.getNodeArray()) {
      dp.setBool(n, newNodes.contains(n));
    }
    graph.addDataProvider(SmartOrganicLayouter.NODE_SUBSET_DATA, dp);
    
    // Remember group node sizes and insets
//    GroupLayoutConfigurator glc = new GroupLayoutConfigurator(graph);
//    glc.prepareAll();
    
    // Create layouter and perform layout
    SmartOrganicLayouter layouter = new SmartOrganicLayouter();
    layouter.setScope(SmartOrganicLayouter.SCOPE_SUBSET);
    // If SmartComponentLayoutEnabled is true, all new nodes will
    // simply be put one above the other. If false, they are lyouted
    // nicely, BUT orphans are being moved, too :-(
//    layouter.setSmartComponentLayoutEnabled(true);
    layouter.setSmartComponentLayoutEnabled(strict);
    layouter.setNodeOverlapsAllowed(newNodes.size()>30);
    layouter.setConsiderNodeLabelsEnabled(true);
    
//    OrganicLayouter layouter = new OrganicLayouter();
//    layouter.setSphereOfAction(OrganicLayouter.ONLY_SELECTION);
    
    Graph2DLayoutExecutor l = new Graph2DLayoutExecutor();
    l.doLayout(graph, layouter);
    
    // Write initial position to node annotations
    for (Node n:newNodes) {
      NodeRealizer nr = graph.getRealizer(n);
      this.setInfo(n, GraphMLmaps.NODE_POSITION, (int) nr.getX() + "|" + (int) nr.getY());
      
      // Paint above other nodes.
      graph.moveToLast(n);
    }
    
    // Restore group node sizes and insets
//    glc.restoreAll();
    
    // remove selection map after layouting.
    graph.removeDataProvider(SmartOrganicLayouter.NODE_SUBSET_DATA);
    
    // Reset layout, because subset scope doesn't work correctly.
    new TranslatorTools(graph).resetLayout();
  }
  
  /**
   * Resets the layout to the information stored in the nodes. Usually
   * this is the layout as given directly by kegg. Only affects X and Y
   * positions, NOT width and height of nodes.
   * @param graph2
   */
  public void resetLayout() {
    DataMap nodeMap = descriptor2Map.get(GraphMLmaps.NODE_POSITION);
    if (nodeMap==null) {
      log.severe("Could not find original node positions.");
      return;
    }
    
    String splitBy = Pattern.quote("|");
    for (Node n: graph.getNodeArray()) {
      Object pos = nodeMap.get(n);
      if (pos==null) continue;
      // pos is always X|Y
      String[] XY = pos.toString().split(splitBy);
      graph.getRealizer(n).setX(Integer.parseInt(XY[0]));
      graph.getRealizer(n).setY(Integer.parseInt(XY[1]));
    }
  }

  /**
   * Reset the color and label of the node to the parameters
   * stored in the map during translation of the pathway.
   * @param n
   */
  public void resetColorAndLabel(Node n) {
    
    // Reset Color
    Object HTMLcolor = getInfo(n, GraphMLmaps.NODE_COLOR);
    if (HTMLcolor!=null) {
      try {
        Color c = KEGG2yGraph.ColorFromHTML(HTMLcolor.toString());
        graph.getRealizer(n).setFillColor(c);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    // Reset name
    Object name = getInfo(n, GraphMLmaps.NODE_NAME);
    if (name!=null) {
      graph.getRealizer(n).setLabelText(name.toString());
    }
    
  }

  /**
   * Resets the node width and height to the values given
   * in the original KGML.
   * @param node
   */
  public void resetWidthAndHeight(Node node) {
    // TODO: Also reset shape
    DataMap nodeMap = descriptor2Map.get(GraphMLmaps.NODE_SIZE);
    if (nodeMap==null) {
      log.severe("Could not find original node sizes.");
      return;
    }
    
    String splitBy = Pattern.quote("|");
    //for (Node node: graph.getNodeArray()) {
    Object pos = nodeMap.get(node);
    if (pos==null) return;//continue;
    // pos is always Width|Height
    String[] WH = pos.toString().split(splitBy);
    graph.getRealizer(node).setWidth(Integer.parseInt(WH[0]));
    graph.getRealizer(node).setHeight(Integer.parseInt(WH[1]));
    //}
  }
  
  /**
   * Removes a data provider from the graph.
   * @param map
   */
  @SuppressWarnings("unchecked")
  public void removeMap(DataMap map) {
    
    // Also remove from my internal map.
    Iterator<Entry<String, DataMap>> it = descriptor2Map.entrySet().iterator();
    while (it.hasNext()) {
      if (it.next().getValue().equals(map)) {
        it.remove();
        break;
      }
    }
    
    // Remove from description map
    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    mapDescriptionMap.removeMapByKey(map, graph);
    
    // Remove from graph
    try {
      if (map instanceof NodeMap) {
        graph.disposeNodeMap((NodeMap) map);
      } else if (map instanceof EdgeMap) {
        graph.disposeEdgeMap((EdgeMap) map);
      }
    } catch (IllegalStateException e) {
      //  Map has been already disposed !
      log.log(Level.FINE, "Could not dispose map.", e);
    }
    
  }
  
  /**
   * Removes a data provider from the graph.
   * @param identifier
   */
  @SuppressWarnings("unchecked")
  public void removeMap(String identifier) {
    
    // Also remove from my internal map.
    DataMap item = descriptor2Map.remove(identifier);
    
    // Remove from description map
    GenericDataMap<DataMap, String> mapDescriptionMap = (GenericDataMap<DataMap, String>) graph.getDataProvider(KEGG2yGraph.mapDescription);
    mapDescriptionMap.removeMap(identifier, graph);
    
    // Remove from graph
    if (item!=null) {
      try {
        if (item instanceof NodeMap) {
          graph.disposeNodeMap((NodeMap) item);
        } else if (item instanceof EdgeMap) {
          graph.disposeEdgeMap((EdgeMap) item);
        }
      } catch (IllegalStateException e) {
        //  Map has been already disposed !
        log.log(Level.FINE, "Could not dispose map.", e);
      }
    }
    
  }

  /**
   * Gets information from a map and returns true if it is not null
   * (i.e. it is set) and if the content equals {@link Boolean#TRUE}.
   * @param node_or_edge and {@link Node} or {@link Edge}
   * @param descriptor a descriptor for a {@link NodeMap} or {@link EdgeMap} in the current
   * {@link #graph}.
   * @return true if {@link #getInfo(Object, String)} !=null and
   * equals <code>TRUE</code>.
   */
  public boolean getBoolInfo(Object node_or_edge, String descriptor) {
    Object o = getInfo(node_or_edge, descriptor);
    
    if (o!=null) {
      if (o instanceof Boolean) return (Boolean)o;
      try {
         return Boolean.valueOf(o.toString());
      } catch (Throwable t) {}
    }
    return false;
  }
  
}
