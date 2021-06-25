package info.kgeorgiy.ja.garipov.student;

import info.kgeorgiy.java.advanced.student.AdvancedQuery;
import info.kgeorgiy.java.advanced.student.Group;
import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.Student;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements AdvancedQuery {
    private final static Function<Student, String> GET_STUDENT_FULL_NAME =
            (Student s) -> s.getFirstName() + " " + s.getLastName();
    private final static Comparator<Student> STUDENT_COMPARATOR = Comparator.
            comparing(Student::getLastName, Comparator.reverseOrder()).
            thenComparing(Student::getFirstName, Comparator.reverseOrder())
            .thenComparing(Student::getId);
    private final static Comparator<Group> GROUP_COMPARATOR = Comparator.comparing(Group::getName);
    private final static ToLongFunction<Map.Entry<GroupName, List<String>>> DISTINCT_NAMES_IN_GROUP =
            (Map.Entry<GroupName, List<String>> entry) -> entry.getValue().stream().distinct().count();
    private final static ToLongFunction<Map.Entry<GroupName, List<String>>> GROUP_SIZE =
            (Map.Entry<GroupName, List<String>> entry) -> entry.getValue().size();
    private final static Comparator<Map.Entry<String, List<GroupName>>> NAME_COUNT_GROUP_COMPARATOR = Comparator.comparing(
            (Map.Entry<String, List<GroupName>> entry) ->
                    entry.getValue().stream().distinct().count()).thenComparing(Map.Entry.comparingByKey());

    private Stream<Group> getGroupsStream(Collection<Student> students, Function<List<Student>, List<Student>> func) {
        return students.stream().
                collect(Collectors.groupingBy(Student::getGroup)).
                entrySet().stream().
                map(entry -> new Group(entry.getKey(), func.apply(entry.getValue())));
    }

    private List<Group> getGroupBy(Collection<Student> students, Function<List<Student>, List<Student>> func) {
        return getGroupsStream(students, func)
                .sorted(GROUP_COMPARATOR)
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupBy(students, this::sortStudentsByName);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupBy(students, this::sortStudentsById);
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getAbstractMaxValue(students,
                Student::getGroup,
                Student::getFirstName,
                getComparator(GROUP_SIZE, Map.Entry.<GroupName, List<String>>comparingByKey()),
                null);
    }

    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getAbstractMaxValue(students,
                Student::getGroup,
                Student::getFirstName,
                getComparator(DISTINCT_NAMES_IN_GROUP,
                        Map.Entry.<GroupName, List<String>>comparingByKey().reversed()),
                null);
    }

    public <T, V> Comparator<Map.Entry<T, List<V>>> getComparator(
            ToLongFunction<Map.Entry<T, List<V>>> objectSizeComparator,
            Comparator<Map.Entry<T, List<V>>> secondComparator) {
        return Comparator.comparingLong(objectSizeComparator).thenComparing(secondComparator);
    }

    public <T, V> T getAbstractMaxValue(Collection<Student> students, Function<Student, T> func1,
                                        Function<Student, V> func2, Comparator<Map.Entry<T, List<V>>> entryComparator,
                                        T defaultValue) {
        return students.stream()
                .collect(Collectors.groupingBy(
                        func1, Collectors.mapping(
                                func2, Collectors.toList())))
                .entrySet().stream()
                .max(entryComparator)
                .map(Map.Entry::getKey).orElse(defaultValue);
    }

    @Override
    public String getMostPopularName(Collection<Student> students) {
        return getAbstractMaxValue(students,
                Student::getFirstName,
                Student::getGroup,
                NAME_COUNT_GROUP_COMPARATOR,
                "");
    }

    private <T> Stream<T> mapStudentsTo(Collection<Student> students, Function<Student, T> func) {
        return students.stream().map(func);
    }

    private <T> List<T> mappedStudentsList(List<Student> students, Function<Student, T> func) {
        return mapStudentsTo(students, func).collect(Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return mappedStudentsList(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return mappedStudentsList(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return mappedStudentsList(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return mappedStudentsList(students, GET_STUDENT_FULL_NAME);
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return mapStudentsTo(students, Student::getFirstName).collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream().max(Comparator.comparing(Student::getId))
                .map(Student::getFirstName).orElse("");
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return students.stream()
                .sorted(Comparator.comparing(Student::getId))
                .collect(Collectors.toList());
    }

    private Stream<Student> sortStudentsStreamByName(Stream<Student> students) {
        return students.sorted(STUDENT_COMPARATOR);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudentsStreamByName(students.stream()).collect(Collectors.toList());
    }

    private <T> Stream<Student> filterStudentsStream(Collection<Student> students, Function<Student, T> func, T value) {
        return students.stream()
                .filter((Student student) -> func.apply(student).equals(value));
    }

    private <T> Stream<Student> streamFindStudentsBy(Collection<Student> students, Function<Student, T> func, T value) {
        return sortStudentsStreamByName(filterStudentsStream(students, func, value));
    }

    private <T> List<Student> findStudentsBy(Collection<Student> students, Function<Student, T> func, T value) {
        return streamFindStudentsBy(students, func, value).collect(Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findStudentsBy(students, Student::getFirstName, name);
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findStudentsBy(students, Student::getLastName, name);
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findStudentsBy(students, Student::getGroup, group);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return filterStudentsStream(students, Student::getGroup, group)
                .collect(Collectors.toMap(
                        Student::getLastName,
                        Student::getFirstName,
                        BinaryOperator.minBy(String::compareTo)
                ));
    }

    private <T> List<T> getByIndexes(Collection<Student> students, Function<Student, T> func, int[] indices) {
        return getByIndexes(Collections.list(Collections.enumeration(students)), func, indices);
    }

    private <T> List<T> getByIndexes(ArrayList<Student> students, Function<Student, T> func, int[] indices) {
        return Arrays.stream(indices)
                .mapToObj(students::get)
                .map(func)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(Collection<Student> students, int[] indices) {
        return getByIndexes(students, Student::getFirstName, indices);
    }

    @Override
    public List<String> getLastNames(Collection<Student> students, int[] indices) {
        return getByIndexes(students, Student::getLastName, indices);
    }

    @Override
    public List<GroupName> getGroups(Collection<Student> students, int[] indices) {
        return getByIndexes(students, Student::getGroup, indices);
    }

    @Override
    public List<String> getFullNames(Collection<Student> students, int[] indices) {
        return getByIndexes(students, GET_STUDENT_FULL_NAME, indices);
    }
}
