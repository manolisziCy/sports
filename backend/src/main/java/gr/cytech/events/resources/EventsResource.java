package gr.cytech.events.resources;

import gr.cytech.events.core.Event;
import gr.cytech.events.daos.EventDao;

import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/event")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class EventsResource extends GenericResource {

    @Inject EventDao eventDao;

    @POST
    @Transactional
    @RolesAllowed("**")
    public Event createEvent(Event event) {
        if (event == null) {
            throw validator.invalidField("event", "Empty event");
        }
        event.id = null;
        return eventDao.persist(event);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RolesAllowed("**")
    public Event updateEvent(@PathParam("id") Long id, Event event) {
        if (event == null || id == null || !id.equals(event.id)) {
            throw validator.invalidField("event", "event not found");
        }
        return eventDao.persist(event);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("**")
    public Event getEvent(@PathParam("id") Long id) {
        return eventDao.getById(id).orElseThrow(NotFoundException::new);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("**")
    @Transactional
    public Event deleteEvent(@PathParam("id") Long id) {
        if (id == null) {
            throw validator.invalidField("event", "event not found");
        }
        var dbEvent = eventDao.getById(id).orElseThrow(() -> {
            throw new NotFoundException();
        });
        if (!dbEvent.recipient.equals(jwt.getSubject())) {
            throw validator.invalidField("event", "event not found");
        }
        var deleted = eventDao.delete(dbEvent);
        if (deleted == null) {
            throw new BadRequestException();
        }
        return dbEvent;
    }
}
