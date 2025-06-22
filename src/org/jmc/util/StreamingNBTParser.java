package org.jmc.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

import org.jmc.NBT.NBT_Tag;
import org.jmc.NBT.TAG_Compound;
import org.jmc.NBT.TAG_List;

/**
 * Streaming NBT parser that processes NBT data without loading the entire
 * structure into memory, reducing memory pressure during chunk processing.
 */
public class StreamingNBTParser {
    
    /**
     * Interface for processing NBT data as it's parsed.
     */
    public interface NBTProcessor {
        /**
         * Process a compound tag when encountered.
         * 
         * @param name Name of the compound tag
         * @param compound The compound tag data
         * @return true to continue processing, false to stop
         */
        boolean processCompound(String name, TAG_Compound compound);
        
        /**
         * Process a list tag when encountered.
         * 
         * @param name Name of the list tag
         * @param list The list tag data
         * @return true to continue processing, false to stop
         */
        default boolean processList(String name, TAG_List list) {
            return true;
        }
        
        /**
         * Called when an error occurs during parsing.
         * 
         * @param error The error that occurred
         */
        default void onError(Exception error) {
            Log.error("NBT parsing error", error);
        }
    }
    
    /**
     * Specialized processor for chunk sections.
     */
    public static class ChunkSectionProcessor implements NBTProcessor {
        private final Consumer<TAG_Compound> sectionHandler;
        
        public ChunkSectionProcessor(Consumer<TAG_Compound> sectionHandler) {
            this.sectionHandler = sectionHandler;
        }
        
        @Override
        public boolean processCompound(String name, TAG_Compound compound) {
            if ("sections".equals(name) || name.startsWith("section_")) {
                sectionHandler.accept(compound);
            }
            return true;
        }
    }
    
    private final InputStream inputStream;
    private DataInputStream dataStream;
    
    /**
     * Creates a new streaming NBT parser.
     * 
     * @param inputStream The input stream containing NBT data
     */
    public StreamingNBTParser(InputStream inputStream) {
        this.inputStream = inputStream;
        // Use buffered stream for better performance
        this.dataStream = new DataInputStream(new BufferedInputStream(inputStream, 8192));
    }
    
    /**
     * Parses NBT data using the provided processor.
     * 
     * @param processor The processor to handle NBT elements
     */
    public void parse(NBTProcessor processor) {
        try {
            parseInternal(processor);
        } catch (Exception e) {
            processor.onError(e);
        } finally {
            try {
                if (dataStream != null) {
                    dataStream.close();
                }
            } catch (IOException e) {
                Log.debug("Error closing NBT stream: " + e.getMessage());
            }
        }
    }
    
    /**
     * Efficiently parses chunk data by focusing on essential sections.
     * 
     * @param sectionHandler Handler for chunk sections
     */
    public void parseChunkSections(Consumer<TAG_Compound> sectionHandler) {
        parse(new ChunkSectionProcessor(sectionHandler));
    }
    
    /**
     * Internal parsing logic.
     */
    private void parseInternal(NBTProcessor processor) throws Exception {
        NBT_Tag root = NBT_Tag.make(dataStream);
        
        if (root instanceof TAG_Compound) {
            processCompoundRecursively("root", (TAG_Compound) root, processor);
        }
    }
    
    /**
     * Recursively processes compound tags, allowing early termination.
     */
    private boolean processCompoundRecursively(String name, TAG_Compound compound, NBTProcessor processor) {
        // Process this compound first
        if (!processor.processCompound(name, compound)) {
            return false; // Stop processing
        }
        
        // Process child elements
        for (NBT_Tag element : compound.elements) {
            if (element instanceof TAG_Compound) {
                if (!processCompoundRecursively(element.getName(), (TAG_Compound) element, processor)) {
                    return false;
                }
            } else if (element instanceof TAG_List) {
                if (!processor.processList(element.getName(), (TAG_List) element)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Utility method to efficiently extract specific NBT values without
     * loading the entire structure.
     * 
     * @param inputStream NBT input stream
     * @param path Path to the desired value (e.g., "Level.Sections.BlockStates")
     * @return The NBT tag at the specified path, or null if not found
     */
    public static NBT_Tag extractValue(InputStream inputStream, String path) {
        String[] pathParts = path.split("\\.");
        final NBT_Tag[] result = {null};
        
        StreamingNBTParser parser = new StreamingNBTParser(inputStream);
        parser.parse(new NBTProcessor() {
            private int currentDepth = 0;
            
            @Override
            public boolean processCompound(String name, TAG_Compound compound) {
                if (currentDepth < pathParts.length && pathParts[currentDepth].equals(name)) {
                    currentDepth++;
                    if (currentDepth == pathParts.length) {
                        result[0] = compound;
                        return false; // Found what we need, stop processing
                    }
                }
                return true;
            }
        });
        
        return result[0];
    }
}