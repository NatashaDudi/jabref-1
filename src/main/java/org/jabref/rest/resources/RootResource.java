package org.jabref.rest.resources;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import javafx.application.Platform;

import org.jabref.gui.Globals;
import org.jabref.gui.StateManager;
import org.jabref.logic.jabmap.BibtexMindMapAdapter;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryAdapter;
import org.jabref.model.jabmap.MindMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.jabref.model.jabmap.MindMapEdge.MAP_EDGE_ENTRY_NAME;
import static org.jabref.model.jabmap.MindMapNode.MAP_NODE_ENTRY_NAME;

@Path("/")
public class RootResource {

    @GET
    @Path("libraries/current/entries")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEntries() {
        // Filter out map and edge entries from list
        List<BibEntry> entries = getActiveDatabase().getEntries().stream().filter(b -> !b.getType().getDisplayName()
                                                                                         .equals(MAP_NODE_ENTRY_NAME) && !b.getType().getDisplayName()
                                                                                                                           .equals(MAP_EDGE_ENTRY_NAME)).collect(Collectors.toList());
        Gson gson = new GsonBuilder().registerTypeAdapter(BibEntry.class, new BibEntryAdapter()).create();
        return Response.status(Response.Status.OK).entity(gson.toJson(entries)).build();
    }

    @GET
    @Path("libraries/current/map")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createBlankMap() {
        Gson gson = new GsonBuilder().create();
        // Retrieve mind map object from database
        BibtexMindMapAdapter adapter = new BibtexMindMapAdapter();
        // Attempt to get a map saved in the current database
        MindMap map = adapter.bibtex2MindMap(getActiveDatabase());
        return Response.status(Response.Status.OK).entity(new Gson().toJson(map)).build();
    }

    @PUT
    @Path("libraries/current/map")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveMindMap(String jsonMindMap) {
        Gson gBuilder = new GsonBuilder().create();
        MindMap map = gBuilder.fromJson(jsonMindMap, MindMap.class);

        // Get adapter to convert to bib entries
        BibtexMindMapAdapter adapter = new BibtexMindMapAdapter();

        addToDatabase(adapter.mindMap2Bibtex(map));

        Response.ResponseBuilder builder = Response.ok();
        return builder.build();
    }

    /**
     * Helper method to get the active database and check it's present
     */
    private BibDatabase getActiveDatabase() {
        StateManager stateManager = Globals.stateManager;
        if (stateManager.getActiveDatabase().isPresent()) {
            return stateManager.getActiveDatabase().get().getDatabase();
        } else {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Helper method to insert entries into database
     */
    private void addToDatabase(List<BibEntry> newEntries) {
        // Get old map entries to remove from database
        List<BibEntry> oldMapEntries = getActiveDatabase().getEntries().stream().filter(b -> b.getType().getDisplayName()
                                                                                              .equals(MAP_NODE_ENTRY_NAME) || b.getType().getDisplayName()
                                                                                                                               .equals(MAP_EDGE_ENTRY_NAME)).collect(Collectors.toList());

        BibDatabase database = getActiveDatabase();
        Platform.runLater(() -> {
                    // Need to run this on the JavaFX thread
                    database.removeEntries(oldMapEntries);
                    database.insertEntries(newEntries);
                }
        );
    }

    /**
     * Helper method to determine if a list of bib entries contains an entry with a certain key
     */
    private Optional<BibEntry> containsEntry(final List<BibEntry> list, final String key) {
        return list.stream().filter(b -> b.getCiteKeyOptional().orElse("").equals(key)).findFirst();
    }
}

