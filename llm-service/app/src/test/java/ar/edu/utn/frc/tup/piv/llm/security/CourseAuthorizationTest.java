package ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security;

import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;

import java.util.UUID;
import ar.edu.utn.frc.tup.piv.llm.application.port.out.CourseMembershipPort;
import org.springframework.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseAuthorizationTest {
  @Test void allowsAssignedTeacher() {
    UUID course = UUID.randomUUID();
    CourseMembershipPort memberships = org.mockito.Mockito.mock(CourseMembershipPort.class);
    UUID teacher = UUID.randomUUID();
    org.mockito.Mockito.when(memberships.membership(org.mockito.Mockito.eq(course), org.mockito.Mockito.eq(teacher), org.mockito.Mockito.any()))
        .thenReturn(new CourseMembershipPort.Membership("DOCENTE", "ACTIVE"));
    assertThatCode(() -> new CourseAuthorization(memberships).requireTeacher(course,
        new CallerIdentity("admin-service", teacher, "request", null), new HttpHeaders())).doesNotThrowAnyException();
  }
  @Test void rejectsUnassignedCourse() {
    CourseMembershipPort memberships = org.mockito.Mockito.mock(CourseMembershipPort.class);
    UUID course = UUID.randomUUID(); UUID teacher = UUID.randomUUID();
    org.mockito.Mockito.when(memberships.membership(org.mockito.Mockito.eq(course), org.mockito.Mockito.eq(teacher), org.mockito.Mockito.any()))
        .thenReturn(new CourseMembershipPort.Membership("DOCENTE", "INACTIVE"));
    assertThatThrownBy(() -> new CourseAuthorization(memberships).requireTeacher(course,
        new CallerIdentity("admin-service", teacher, "request", null), new HttpHeaders())).isInstanceOf(RuntimeException.class);
  }
  @Test void rejectsNonTeacherRole() {
    CourseMembershipPort memberships = org.mockito.Mockito.mock(CourseMembershipPort.class);
    UUID course = UUID.randomUUID(); UUID learner = UUID.randomUUID();
    org.mockito.Mockito.when(memberships.membership(org.mockito.Mockito.eq(course), org.mockito.Mockito.eq(learner), org.mockito.Mockito.any()))
        .thenReturn(new CourseMembershipPort.Membership("ESTUDIANTE", "ACTIVE"));
    assertThatThrownBy(() -> new CourseAuthorization(memberships).requireTeacher(course,
        new CallerIdentity("admin-service", learner, "request", null), new HttpHeaders())).isInstanceOf(RuntimeException.class);
  }
}
