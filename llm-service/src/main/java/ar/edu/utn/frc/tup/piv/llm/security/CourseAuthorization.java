package ar.edu.utn.frc.tup.piv.llm.security;

import java.util.UUID;
import ar.edu.utn.frc.tup.piv.llm.application.CourseMembershipPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Authorizes course-scoped teacher operations against the owner: courses-service. */
@Component
public class CourseAuthorization {
  private final CourseMembershipPort memberships;

  public CourseAuthorization(CourseMembershipPort memberships) { this.memberships = memberships; }

  public void requireTeacher(UUID courseId, CallerIdentity actor, HttpHeaders headers) {
    if (courseId == null || actor == null || actor.delegatedUserId() == null
        || !memberships.membership(courseId, actor.delegatedUserId(), actor).isActiveTeacher()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El actor no puede administrar este curso");
    }
  }
}
