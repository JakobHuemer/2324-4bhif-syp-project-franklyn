use ab_glyph::{FontArc, PxScale};
use image::{Rgba, RgbaImage};
use imageproc::drawing::{draw_filled_rect_mut, draw_text_mut};
use imageproc::rect::Rect;

// Colors (dark theme IDE style)
const BG_COLOR: Rgba<u8> = Rgba([30, 30, 30, 255]); // Main background
const EXPLORER_BG: Rgba<u8> = Rgba([37, 37, 38, 255]); // File explorer
const TERMINAL_BG: Rgba<u8> = Rgba([24, 24, 24, 255]); // Terminal
const EDITOR_BG: Rgba<u8> = Rgba([30, 30, 30, 255]); // Editor background
const LINE_NUM_BG: Rgba<u8> = Rgba([30, 30, 30, 255]); // Line numbers bg
const STATUS_BAR: Rgba<u8> = Rgba([0, 122, 204, 255]); // Blue status bar
const TAB_BG: Rgba<u8> = Rgba([45, 45, 45, 255]); // Tab background
const TAB_ACTIVE: Rgba<u8> = Rgba([30, 30, 30, 255]); // Active tab

// Browser colors
const BROWSER_BG: Rgba<u8> = Rgba([255, 255, 255, 255]); // Browser background
const BROWSER_TOOLBAR: Rgba<u8> = Rgba([240, 240, 240, 255]); // Browser toolbar
const BROWSER_TAB_BAR: Rgba<u8> = Rgba([222, 225, 230, 255]); // Browser tab bar
const STACKOVERFLOW_ORANGE: Rgba<u8> = Rgba([244, 128, 36, 255]); // SO orange
const CHATGPT_DARK: Rgba<u8> = Rgba([52, 53, 65, 255]); // ChatGPT dark bg
const CHATGPT_SIDEBAR: Rgba<u8> = Rgba([32, 33, 35, 255]); // ChatGPT sidebar
const CHATGPT_MSG_USER: Rgba<u8> = Rgba([52, 53, 65, 255]); // User message bg
const CHATGPT_MSG_AI: Rgba<u8> = Rgba([68, 70, 84, 255]); // AI message bg
const QUARKUS_BLUE: Rgba<u8> = Rgba([79, 70, 229, 255]); // Quarkus purple-blue

// Text colors
const TEXT_WHITE: Rgba<u8> = Rgba([212, 212, 212, 255]);
const TEXT_GRAY: Rgba<u8> = Rgba([128, 128, 128, 255]);
const TEXT_KEYWORD: Rgba<u8> = Rgba([197, 134, 192, 255]); // Purple for keywords
const TEXT_STRING: Rgba<u8> = Rgba([206, 145, 120, 255]); // Orange for strings
const TEXT_TYPE: Rgba<u8> = Rgba([78, 201, 176, 255]); // Teal for types
const TEXT_COMMENT: Rgba<u8> = Rgba([106, 153, 85, 255]); // Green for comments
const TEXT_NUMBER: Rgba<u8> = Rgba([181, 206, 168, 255]); // Light green for numbers
const TEXT_BROWSER: Rgba<u8> = Rgba([30, 30, 30, 255]); // Dark text for browser
const TEXT_LINK: Rgba<u8> = Rgba([0, 116, 204, 255]); // Blue links

// Screen dimensions
const WIDTH: u32 = 1920;
const HEIGHT: u32 = 1080;

// IDE Layout dimensions
const EXPLORER_WIDTH: u32 = 250;
const TERMINAL_HEIGHT: u32 = 200;
const STATUS_BAR_HEIGHT: u32 = 24;
const TAB_HEIGHT: u32 = 35;
const LINE_NUM_WIDTH: u32 = 50;

// Browser layout dimensions
const BROWSER_TAB_HEIGHT: u32 = 40;
const BROWSER_TOOLBAR_HEIGHT: u32 = 50;
const BROWSER_HEADER_HEIGHT: u32 = BROWSER_TAB_HEIGHT + BROWSER_TOOLBAR_HEIGHT;

// Font
const FONT_DATA: &[u8] = include_bytes!("../assets/DejaVuSansMono.ttf");

pub struct MockScreenGenerator {
    font: FontArc,
    frame_counter: u32,
    current_file: FileType,
    current_browser: BrowserPage,
    typing_progress: usize,
    show_browser: bool,
}

#[derive(Clone, Copy)]
pub enum FileType {
    Java,
    Properties,
    PomXml,
    Shell,
}

#[derive(Clone, Copy)]
pub enum BrowserPage {
    ExamTask,
    StackOverflow,
    ChatGPT,
    QuarkusDocs,
    JavaDocs,
}

impl BrowserPage {
    fn url(&self) -> &'static str {
        match self {
            BrowserPage::ExamTask => "https://exam.htl-leonding.ac.at/task/1",
            BrowserPage::StackOverflow => {
                "https://stackoverflow.com/questions/12345678/jpa-entity-not-persisting-to-database"
            }
            BrowserPage::ChatGPT => "https://chat.openai.com/c/a1b2c3d4-e5f6-7890",
            BrowserPage::QuarkusDocs => "https://quarkus.io/guides/hibernate-orm",
            BrowserPage::JavaDocs => {
                "https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html"
            }
        }
    }

    fn tab_title(&self) -> &'static str {
        match self {
            BrowserPage::ExamTask => "HTL Leonding Exam",
            BrowserPage::StackOverflow => "JPA Entity not persisting - Stack Overflow",
            BrowserPage::ChatGPT => "ChatGPT",
            BrowserPage::QuarkusDocs => "Using Hibernate ORM - Quarkus",
            BrowserPage::JavaDocs => "List (Java SE 17) - Oracle",
        }
    }

    fn next(&self) -> BrowserPage {
        match self {
            BrowserPage::ExamTask => BrowserPage::StackOverflow,
            BrowserPage::StackOverflow => BrowserPage::ChatGPT,
            BrowserPage::ChatGPT => BrowserPage::QuarkusDocs,
            BrowserPage::QuarkusDocs => BrowserPage::JavaDocs,
            BrowserPage::JavaDocs => BrowserPage::ExamTask,
        }
    }
}

impl FileType {
    fn filename(&self) -> &'static str {
        match self {
            FileType::Java => "Student.java",
            FileType::Properties => "application.properties",
            FileType::PomXml => "pom.xml",
            FileType::Shell => "run.sh",
        }
    }

    fn content(&self) -> &'static str {
        match self {
            FileType::Java => JAVA_CODE,
            FileType::Properties => PROPERTIES_CODE,
            FileType::PomXml => POM_XML_CODE,
            FileType::Shell => SHELL_CODE,
        }
    }
}

const JAVA_CODE: &str = r#"package at.htl.exam;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
public class Student {
    @Id
    @GeneratedValue
    private Long id;
    private String firstName;
    private String lastName;
    private int matriculationNumber;

    public Student() {
    }

    public Student(String firstName, String lastName, int matriculationNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.matriculationNumber = matriculationNumber;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getMatriculationNumber() {
        return matriculationNumber;
    }

    public void setMatriculationNumber(int matriculationNumber) {
        this.matriculationNumber = matriculationNumber;
    }
}
"#;

const PROPERTIES_CODE: &str = r#"# Database Configuration
quarkus.datasource.db-kind=postgresql
quarkus.datasource.username=app
quarkus.datasource.password=secret
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/examdb

# Hibernate
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.hibernate-orm.log.sql=true

# HTTP
quarkus.http.port=8080
quarkus.http.cors=true

# Logging
quarkus.log.console.enable=true
quarkus.log.level=INFO
"#;

const POM_XML_CODE: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>at.htl.exam</groupId>
    <artifactId>student-management</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <quarkus.version>3.8.0</quarkus.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-orm</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-resteasy-reactive</artifactId>
        </dependency>
    </dependencies>
</project>
"#;

const SHELL_CODE: &str = r#"#!/bin/bash

# Build and run the application
echo "Building project..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "Starting application..."
    java -jar target/student-management-1.0.0-SNAPSHOT-runner.jar
else
    echo "Build failed!"
    exit 1
fi
"#;

const TERMINAL_OUTPUT: &str = r#"$ mvn quarkus:dev
[INFO] Scanning for projects...
[INFO] 
[INFO] ------------------< at.htl.exam:student-management >------------------
[INFO] Building student-management 1.0.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
Listening for transport dt_socket at address: 5005
__  ____  __  _____   ___  __ ____  ______ 
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
2024-01-15 10:23:45,123 INFO  [io.quarkus] Quarkus 3.8.0 started
"#;

// Browser page contents
const EXAM_CONTENT: &[&str] = &[
    "",
    "HTL Leonding - Programming Exam",
    "================================",
    "",
    "Student: {{firstname}} {{lastname}}",
    "Time remaining: 01:45:32",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Task 1: Entity Implementation (30 points)",
    "",
    "Create a JPA Entity class \"Student\" with the following attributes:",
    "  - id (Long, auto-generated primary key)",
    "  - firstName (String, max 50 characters)",
    "  - lastName (String, max 50 characters)",
    "  - matriculationNumber (int, unique)",
    "",
    "Requirements:",
    "  - Use appropriate JPA annotations",
    "  - Implement default constructor",
    "  - Implement parameterized constructor",
    "  - Implement all getters and setters",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Task 2: Repository Pattern (25 points)",
    "",
    "Implement a repository class for CRUD operations on the Student entity.",
    "  - Use Panache or standard EntityManager",
    "  - Implement findAll(), findById(), save(), delete()",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Task 3: REST Endpoint (25 points)",
    "",
    "Create REST endpoints for student management:",
    "  - GET  /api/students         - List all students",
    "  - GET  /api/students/{id}    - Get student by ID",
    "  - POST /api/students         - Create new student",
    "  - PUT  /api/students/{id}    - Update student",
    "  - DELETE /api/students/{id}  - Delete student",
    "",
];

const STACKOVERFLOW_CONTENT: &[&str] = &[
    "",
    "JPA Entity not persisting to database",
    "",
    "Asked 2 hours ago    Modified 1 hour ago    Viewed 1,247 times",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "I have created a JPA entity in my Quarkus application but the data is not",
    "being saved to the database. Here is my entity:",
    "",
    "    @Entity",
    "    public class Student {",
    "        @Id",
    "        private Long id;",
    "        private String name;",
    "    }",
    "",
    "When I try to persist, nothing happens. What am I missing?",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "3 Answers                                           Sorted by: Highest score",
    "",
    "================================================================================",
    "",
    "[Accepted Answer]  Score: 127",
    "",
    "You're missing the @GeneratedValue annotation on your @Id field.",
    "Without it, JPA expects you to manually set the ID before persisting.",
    "",
    "Change your entity to:",
    "",
    "    @Entity",
    "    public class Student {",
    "        @Id",
    "        @GeneratedValue(strategy = GenerationType.IDENTITY)",
    "        private Long id;",
    "        private String name;",
    "    }",
    "",
    "Also make sure you have a no-args constructor (required by JPA).",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Score: 45",
    "",
    "In addition to the above, check your application.properties:",
    "  quarkus.hibernate-orm.database.generation=drop-and-create",
    "",
];

const CHATGPT_CONTENT: &[&str] = &[
    "USER:",
    "How do I create a JPA entity in Quarkus with auto-generated ID?",
    "",
    "ASSISTANT:",
    "To create a JPA entity in Quarkus with an auto-generated ID, follow these",
    "steps:",
    "",
    "1. First, add the required dependencies to your pom.xml:",
    "",
    "   <dependency>",
    "       <groupId>io.quarkus</groupId>",
    "       <artifactId>quarkus-hibernate-orm-panache</artifactId>",
    "   </dependency>",
    "",
    "2. Create your entity class with the @Entity annotation:",
    "",
    "   @Entity",
    "   public class Student {",
    "       @Id",
    "       @GeneratedValue(strategy = GenerationType.IDENTITY)",
    "       private Long id;",
    "       ",
    "       private String firstName;",
    "       private String lastName;",
    "       ",
    "       // Default constructor (required by JPA)",
    "       public Student() {}",
    "       ",
    "       // Getters and setters...",
    "   }",
    "",
    "3. Configure your datasource in application.properties:",
    "",
    "   quarkus.datasource.db-kind=postgresql",
    "   quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/mydb",
    "   quarkus.hibernate-orm.database.generation=drop-and-create",
    "",
    "The @GeneratedValue annotation tells JPA to automatically generate unique",
    "IDs for new entities. IDENTITY strategy uses the database's auto-increment",
    "feature.",
    "",
    "USER:",
    "What about validation annotations?",
    "",
    "ASSISTANT:",
    "Great question! You can add Bean Validation annotations to your entity:",
    "",
    "   @Entity",
    "   public class Student {",
    "       @Id",
    "       @GeneratedValue",
    "       private Long id;",
    "       ",
    "       @NotBlank",
    "       @Size(max = 50)",
    "       private String firstName;",
    "",
];

const QUARKUS_DOCS_CONTENT: &[&str] = &[
    "",
    "QUARKUS - USING HIBERNATE ORM AND JPA",
    "=====================================",
    "",
    "Hibernate ORM is the de facto JPA implementation and offers you the full",
    "breadth of an Object Relational Mapper. It makes complex mappings possible.",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Setting up and configuring Hibernate ORM",
    "",
    "1. Add the Hibernate ORM extension to your project:",
    "",
    "   ./mvnw quarkus:add-extension -Dextensions=\"hibernate-orm,jdbc-postgresql\"",
    "",
    "2. Configure your datasource in application.properties:",
    "",
    "   # Datasource configuration",
    "   quarkus.datasource.db-kind=postgresql",
    "   quarkus.datasource.username=developer",
    "   quarkus.datasource.password=developer",
    "   quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/my_database",
    "",
    "   # Hibernate configuration",
    "   quarkus.hibernate-orm.database.generation=drop-and-create",
    "   quarkus.hibernate-orm.log.sql=true",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Defining your entity",
    "",
    "   @Entity",
    "   @Table(name = \"students\")",
    "   public class Student {",
    "",
    "       @Id",
    "       @GeneratedValue(strategy = GenerationType.IDENTITY)",
    "       private Long id;",
    "",
    "       @Column(length = 50, nullable = false)",
    "       private String firstName;",
    "",
    "       @Column(length = 50, nullable = false)",
    "       private String lastName;",
    "",
    "       // constructors, getters, setters",
    "   }",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "See also:",
    "  - Hibernate ORM with Panache guide",
    "  - Datasource configuration reference",
    "",
];

const JAVADOCS_CONTENT: &[&str] = &[
    "",
    "Java SE 17 & JDK 17 - API Documentation",
    "=======================================",
    "",
    "Module java.base  >  Package java.util  >  Interface List<E>",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Interface List<E>",
    "",
    "Type Parameters:",
    "    E - the type of elements in this list",
    "",
    "All Superinterfaces:",
    "    Collection<E>, Iterable<E>",
    "",
    "All Known Implementing Classes:",
    "    AbstractList, ArrayList, LinkedList, Vector, Stack,",
    "    CopyOnWriteArrayList",
    "",
    "--------------------------------------------------------------------------------",
    "",
    "Method Summary",
    "",
    "  boolean add(E e)",
    "      Appends the specified element to the end of this list.",
    "",
    "  void add(int index, E element)",
    "      Inserts the specified element at the specified position.",
    "",
    "  boolean addAll(Collection<? extends E> c)",
    "      Appends all elements in the specified collection.",
    "",
    "  void clear()",
    "      Removes all of the elements from this list.",
    "",
    "  boolean contains(Object o)",
    "      Returns true if this list contains the specified element.",
    "",
    "  E get(int index)",
    "      Returns the element at the specified position in this list.",
    "",
    "  int indexOf(Object o)",
    "      Returns the index of the first occurrence of the element.",
    "",
    "  boolean isEmpty()",
    "      Returns true if this list contains no elements.",
    "",
    "  E remove(int index)",
    "      Removes the element at the specified position.",
    "",
    "  int size()",
    "      Returns the number of elements in this list.",
    "",
];

impl MockScreenGenerator {
    /// Create a generator at a specific frame index (for parallel generation)
    pub fn new_at_frame(client_id: u32, frame_index: u32) -> Self {
        let font = FontArc::try_from_slice(FONT_DATA).expect("Failed to load font");

        // Use client_id to vary starting state
        let file_types = [
            FileType::Java,
            FileType::Properties,
            FileType::PomXml,
            FileType::Shell,
        ];
        let browser_pages = [
            BrowserPage::ExamTask,
            BrowserPage::StackOverflow,
            BrowserPage::ChatGPT,
            BrowserPage::QuarkusDocs,
            BrowserPage::JavaDocs,
        ];

        // Calculate base offset for this client
        let base_counter = client_id * 100;
        let frame_counter = base_counter + frame_index;

        // Calculate state at this frame index
        let current_file_idx =
            ((client_id as usize) + (frame_index as usize / 200)) % file_types.len();
        let current_file = file_types[current_file_idx];

        let current_browser_idx =
            ((client_id as usize) + (frame_counter as usize / 60)) % browser_pages.len();
        let current_browser = browser_pages[current_browser_idx];

        // Calculate typing progress: resets every 200 frames when file changes
        let frames_since_file_change = frame_index % 200;
        let base_typing = (client_id as usize * 50) % 500;
        let typing_progress = base_typing + (frames_since_file_change as usize * 3);

        // Calculate browser/IDE toggle state
        // Starts with browser if client_id % 3 == 0
        // Toggles every 25 frames
        let initial_browser = client_id % 3 == 0;
        let toggle_count = frame_counter / 25;
        let show_browser = if toggle_count % 2 == 0 {
            initial_browser
        } else {
            !initial_browser
        };

        Self {
            font,
            frame_counter,
            current_file,
            current_browser,
            typing_progress,
            show_browser,
        }
    }

    pub fn generate_frame(&mut self) -> RgbaImage {
        let mut img = RgbaImage::from_pixel(WIDTH, HEIGHT, BG_COLOR);

        if self.show_browser {
            // Full screen browser
            self.draw_full_browser(&mut img);
        } else {
            // Full screen IDE
            self.draw_explorer(&mut img);
            self.draw_tabs(&mut img);
            self.draw_editor(&mut img);
            self.draw_terminal(&mut img);
            self.draw_status_bar(&mut img);
        }

        // Advance state for next frame
        self.frame_counter += 1;
        self.typing_progress += 3; // Simulate typing

        // Toggle between browser and IDE (simulating Alt+Tab)
        if self.frame_counter % 25 == 0 {
            self.show_browser = !self.show_browser;
        }

        // Cycle browser pages when switching to browser
        if self.frame_counter % 60 == 0 {
            self.current_browser = self.current_browser.next();
        }

        // Cycle through files occasionally
        if self.frame_counter % 200 == 0 {
            self.current_file = match self.current_file {
                FileType::Java => FileType::Properties,
                FileType::Properties => FileType::PomXml,
                FileType::PomXml => FileType::Shell,
                FileType::Shell => FileType::Java,
            };
            self.typing_progress = 0;
        }

        img
    }

    fn draw_full_browser(&self, img: &mut RgbaImage) {
        match self.current_browser {
            BrowserPage::ChatGPT => self.draw_chatgpt_fullscreen(img),
            BrowserPage::StackOverflow => self.draw_stackoverflow_fullscreen(img),
            BrowserPage::QuarkusDocs => self.draw_quarkus_fullscreen(img),
            BrowserPage::JavaDocs => self.draw_javadocs_fullscreen(img),
            BrowserPage::ExamTask => self.draw_exam_fullscreen(img),
        }
    }

    fn draw_browser_chrome(&self, img: &mut RgbaImage, dark_mode: bool) {
        let tab_bg = if dark_mode {
            Rgba([40, 40, 40, 255])
        } else {
            BROWSER_TAB_BAR
        };
        let toolbar_bg = if dark_mode {
            Rgba([50, 50, 50, 255])
        } else {
            BROWSER_TOOLBAR
        };
        let text_color = if dark_mode { TEXT_WHITE } else { TEXT_BROWSER };

        // Tab bar
        draw_filled_rect_mut(
            img,
            Rect::at(0, 0).of_size(WIDTH, BROWSER_TAB_HEIGHT),
            tab_bg,
        );

        // Active tab
        let active_tab_bg = if dark_mode {
            Rgba([60, 60, 60, 255])
        } else {
            BROWSER_BG
        };
        draw_filled_rect_mut(
            img,
            Rect::at(10, 5).of_size(300, BROWSER_TAB_HEIGHT - 5),
            active_tab_bg,
        );

        // Tab title
        let scale = PxScale::from(12.0);
        draw_text_mut(
            img,
            text_color,
            20,
            12,
            scale,
            &self.font,
            self.current_browser.tab_title(),
        );

        // Toolbar with URL bar
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_TAB_HEIGHT as i32).of_size(WIDTH, BROWSER_TOOLBAR_HEIGHT),
            toolbar_bg,
        );

        // URL bar background
        let url_bar_bg = if dark_mode {
            Rgba([35, 35, 35, 255])
        } else {
            Rgba([255, 255, 255, 255])
        };
        draw_filled_rect_mut(
            img,
            Rect::at(100, BROWSER_TAB_HEIGHT as i32 + 10).of_size(WIDTH - 200, 30),
            url_bar_bg,
        );

        // URL text
        let scale = PxScale::from(13.0);
        draw_text_mut(
            img,
            text_color,
            115,
            BROWSER_TAB_HEIGHT as i32 + 17,
            scale,
            &self.font,
            self.current_browser.url(),
        );

        // Navigation buttons placeholder
        let scale = PxScale::from(16.0);
        draw_text_mut(
            img,
            text_color,
            20,
            BROWSER_TAB_HEIGHT as i32 + 15,
            scale,
            &self.font,
            "<  >",
        );
    }

    fn draw_exam_fullscreen(&self, img: &mut RgbaImage) {
        // White background
        draw_filled_rect_mut(img, Rect::at(0, 0).of_size(WIDTH, HEIGHT), BROWSER_BG);

        self.draw_browser_chrome(img, false);

        // Exam header bar
        let header_color = Rgba([0, 85, 164, 255]); // HTL blue
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32).of_size(WIDTH, 60),
            header_color,
        );

        let scale = PxScale::from(24.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            50,
            BROWSER_HEADER_HEIGHT as i32 + 18,
            scale,
            &self.font,
            "HTL LEONDING - Online Examination System",
        );

        // Content area
        let content_y = BROWSER_HEADER_HEIGHT + 80;
        let scale = PxScale::from(15.0);

        for (i, line) in EXAM_CONTENT.iter().enumerate() {
            if (content_y + (i as u32 * 22)) > HEIGHT - 50 {
                break;
            }

            let color = if line.contains("Task") && line.contains("points") {
                Rgba([0, 100, 180, 255]) // Blue for task headers
            } else if line.starts_with("  -") {
                TEXT_GRAY
            } else if line.contains("===") || line.contains("---") {
                Rgba([180, 180, 180, 255])
            } else {
                TEXT_BROWSER
            };

            draw_text_mut(
                img,
                color,
                50,
                content_y as i32 + (i as i32 * 22),
                scale,
                &self.font,
                line,
            );
        }
    }

    fn draw_stackoverflow_fullscreen(&self, img: &mut RgbaImage) {
        // White background
        draw_filled_rect_mut(img, Rect::at(0, 0).of_size(WIDTH, HEIGHT), BROWSER_BG);

        self.draw_browser_chrome(img, false);

        // SO header
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32).of_size(WIDTH, 55),
            Rgba([248, 249, 249, 255]),
        );

        // SO logo bar
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32).of_size(WIDTH, 50),
            STACKOVERFLOW_ORANGE,
        );

        let scale = PxScale::from(22.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            50,
            BROWSER_HEADER_HEIGHT as i32 + 12,
            scale,
            &self.font,
            "stack overflow",
        );

        // Left sidebar placeholder
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32 + 50)
                .of_size(200, HEIGHT - BROWSER_HEADER_HEIGHT - 50),
            Rgba([248, 249, 249, 255]),
        );

        // Content
        let content_y = BROWSER_HEADER_HEIGHT + 70;
        let scale = PxScale::from(14.0);

        for (i, line) in STACKOVERFLOW_CONTENT.iter().enumerate() {
            if (content_y + (i as u32 * 20)) > HEIGHT - 30 {
                break;
            }

            let color = if line.starts_with("    @") {
                TEXT_TYPE
            } else if line.contains("private") || line.contains("public") {
                TEXT_KEYWORD
            } else if line.contains("[Accepted") {
                Rgba([0, 128, 0, 255])
            } else if line.starts_with("Score:") {
                TEXT_GRAY
            } else if line.contains("===") || line.contains("---") {
                Rgba([200, 200, 200, 255])
            } else {
                TEXT_BROWSER
            };

            draw_text_mut(
                img,
                color,
                220,
                content_y as i32 + (i as i32 * 20),
                scale,
                &self.font,
                line,
            );
        }

        // Vote arrows placeholder on the left
        let scale = PxScale::from(20.0);
        draw_text_mut(
            img,
            TEXT_GRAY,
            230,
            content_y as i32 + 200,
            scale,
            &self.font,
            "^",
        );
        draw_text_mut(
            img,
            STACKOVERFLOW_ORANGE,
            225,
            content_y as i32 + 225,
            scale,
            &self.font,
            "127",
        );
        draw_text_mut(
            img,
            TEXT_GRAY,
            230,
            content_y as i32 + 255,
            scale,
            &self.font,
            "v",
        );
    }

    fn draw_chatgpt_fullscreen(&self, img: &mut RgbaImage) {
        // Dark background
        draw_filled_rect_mut(img, Rect::at(0, 0).of_size(WIDTH, HEIGHT), CHATGPT_DARK);

        self.draw_browser_chrome(img, true);

        // Sidebar
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32).of_size(260, HEIGHT - BROWSER_HEADER_HEIGHT),
            CHATGPT_SIDEBAR,
        );

        // Sidebar content
        let scale = PxScale::from(13.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 20,
            scale,
            &self.font,
            "+ New chat",
        );
        draw_text_mut(
            img,
            TEXT_GRAY,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 60,
            scale,
            &self.font,
            "Today",
        );
        draw_text_mut(
            img,
            TEXT_WHITE,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 85,
            scale,
            &self.font,
            "JPA Entity Question",
        );
        draw_text_mut(
            img,
            TEXT_WHITE,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 110,
            scale,
            &self.font,
            "Quarkus Setup Help",
        );
        draw_text_mut(
            img,
            TEXT_GRAY,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 150,
            scale,
            &self.font,
            "Yesterday",
        );
        draw_text_mut(
            img,
            TEXT_WHITE,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 175,
            scale,
            &self.font,
            "REST API Design",
        );

        // Chat content area
        let chat_x = 280;
        let content_y = BROWSER_HEADER_HEIGHT + 30;
        let scale = PxScale::from(14.0);

        let mut y = content_y as i32;
        for line in CHATGPT_CONTENT.iter() {
            if y > (HEIGHT - 100) as i32 {
                break;
            }

            if line.starts_with("USER:") {
                // User message bubble
                y += 15;
                draw_filled_rect_mut(
                    img,
                    Rect::at(chat_x, y - 5).of_size(WIDTH - chat_x as u32 - 50, 30),
                    CHATGPT_MSG_USER,
                );
                draw_text_mut(
                    img,
                    Rgba([180, 180, 180, 255]),
                    chat_x + 10,
                    y,
                    scale,
                    &self.font,
                    "You",
                );
                y += 30;
            } else if line.starts_with("ASSISTANT:") {
                // AI response
                y += 15;
                draw_filled_rect_mut(
                    img,
                    Rect::at(chat_x, y - 5).of_size(WIDTH - chat_x as u32 - 50, 350),
                    CHATGPT_MSG_AI,
                );
                draw_text_mut(
                    img,
                    Rgba([180, 180, 180, 255]),
                    chat_x + 10,
                    y,
                    scale,
                    &self.font,
                    "ChatGPT",
                );
                y += 30;
            } else {
                let color =
                    if line.contains("@Entity") || line.contains("@Id") || line.contains("@") {
                        TEXT_TYPE
                    } else if line.contains("<dependency>") || line.contains("<groupId>") {
                        Rgba([150, 200, 150, 255])
                    } else {
                        TEXT_WHITE
                    };
                draw_text_mut(img, color, chat_x + 15, y, scale, &self.font, line);
                y += 22;
            }
        }

        // Input box at bottom
        draw_filled_rect_mut(
            img,
            Rect::at(chat_x, (HEIGHT - 80) as i32).of_size(WIDTH - chat_x as u32 - 50, 50),
            Rgba([64, 65, 79, 255]),
        );
        let scale = PxScale::from(14.0);
        draw_text_mut(
            img,
            TEXT_GRAY,
            chat_x + 20,
            (HEIGHT - 60) as i32,
            scale,
            &self.font,
            "Send a message...",
        );
    }

    fn draw_quarkus_fullscreen(&self, img: &mut RgbaImage) {
        // White background
        draw_filled_rect_mut(img, Rect::at(0, 0).of_size(WIDTH, HEIGHT), BROWSER_BG);

        self.draw_browser_chrome(img, false);

        // Quarkus header
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32).of_size(WIDTH, 60),
            QUARKUS_BLUE,
        );

        let scale = PxScale::from(26.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            50,
            BROWSER_HEADER_HEIGHT as i32 + 15,
            scale,
            &self.font,
            "QUARKUS",
        );

        let scale = PxScale::from(14.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            200,
            BROWSER_HEADER_HEIGHT as i32 + 22,
            scale,
            &self.font,
            "GUIDES    EXTENSIONS    COMMUNITY    BLOG",
        );

        // Sidebar
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32 + 60)
                .of_size(250, HEIGHT - BROWSER_HEADER_HEIGHT - 60),
            Rgba([248, 249, 250, 255]),
        );

        let scale = PxScale::from(12.0);
        draw_text_mut(
            img,
            QUARKUS_BLUE,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 80,
            scale,
            &self.font,
            "Getting Started",
        );
        draw_text_mut(
            img,
            TEXT_BROWSER,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 105,
            scale,
            &self.font,
            "Core",
        );
        draw_text_mut(
            img,
            QUARKUS_BLUE,
            30,
            BROWSER_HEADER_HEIGHT as i32 + 125,
            scale,
            &self.font,
            "> Hibernate ORM",
        );
        draw_text_mut(
            img,
            TEXT_BROWSER,
            30,
            BROWSER_HEADER_HEIGHT as i32 + 145,
            scale,
            &self.font,
            "  RESTEasy",
        );
        draw_text_mut(
            img,
            TEXT_BROWSER,
            30,
            BROWSER_HEADER_HEIGHT as i32 + 165,
            scale,
            &self.font,
            "  Security",
        );
        draw_text_mut(
            img,
            TEXT_BROWSER,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 190,
            scale,
            &self.font,
            "Data",
        );
        draw_text_mut(
            img,
            TEXT_BROWSER,
            20,
            BROWSER_HEADER_HEIGHT as i32 + 215,
            scale,
            &self.font,
            "Messaging",
        );

        // Content
        let content_y = BROWSER_HEADER_HEIGHT + 80;
        let scale = PxScale::from(14.0);

        for (i, line) in QUARKUS_DOCS_CONTENT.iter().enumerate() {
            if (content_y + (i as u32 * 20)) > HEIGHT - 30 {
                break;
            }

            let color = if line.contains("===") {
                QUARKUS_BLUE
            } else if line.starts_with("    @") {
                TEXT_TYPE
            } else if line.contains("./mvnw") {
                Rgba([100, 100, 100, 255])
            } else if line.contains("quarkus.") {
                Rgba([0, 100, 0, 255])
            } else if line.contains("---") {
                Rgba([200, 200, 200, 255])
            } else {
                TEXT_BROWSER
            };

            draw_text_mut(
                img,
                color,
                280,
                content_y as i32 + (i as i32 * 20),
                scale,
                &self.font,
                line,
            );
        }
    }

    fn draw_javadocs_fullscreen(&self, img: &mut RgbaImage) {
        // White background
        draw_filled_rect_mut(img, Rect::at(0, 0).of_size(WIDTH, HEIGHT), BROWSER_BG);

        self.draw_browser_chrome(img, false);

        // Oracle header
        let oracle_red = Rgba([180, 50, 50, 255]);
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32).of_size(WIDTH, 50),
            oracle_red,
        );

        let scale = PxScale::from(18.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            50,
            BROWSER_HEADER_HEIGHT as i32 + 14,
            scale,
            &self.font,
            "ORACLE  |  Java SE 17 Documentation",
        );

        // Navigation breadcrumb
        let scale = PxScale::from(12.0);
        draw_text_mut(
            img,
            TEXT_LINK,
            50,
            BROWSER_HEADER_HEIGHT as i32 + 65,
            scale,
            &self.font,
            "java.base > java.util > List",
        );

        // Left sidebar - package list
        draw_filled_rect_mut(
            img,
            Rect::at(0, BROWSER_HEADER_HEIGHT as i32 + 85)
                .of_size(220, HEIGHT - BROWSER_HEADER_HEIGHT - 85),
            Rgba([248, 248, 248, 255]),
        );

        draw_text_mut(
            img,
            TEXT_BROWSER,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 100,
            scale,
            &self.font,
            "All Classes",
        );
        draw_text_mut(
            img,
            TEXT_LINK,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 120,
            scale,
            &self.font,
            "ArrayList",
        );
        draw_text_mut(
            img,
            TEXT_LINK,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 138,
            scale,
            &self.font,
            "HashMap",
        );
        draw_text_mut(
            img,
            oracle_red,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 156,
            scale,
            &self.font,
            "> List",
        );
        draw_text_mut(
            img,
            TEXT_LINK,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 174,
            scale,
            &self.font,
            "Map",
        );
        draw_text_mut(
            img,
            TEXT_LINK,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 192,
            scale,
            &self.font,
            "Set",
        );
        draw_text_mut(
            img,
            TEXT_LINK,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 210,
            scale,
            &self.font,
            "Optional",
        );
        draw_text_mut(
            img,
            TEXT_LINK,
            15,
            BROWSER_HEADER_HEIGHT as i32 + 228,
            scale,
            &self.font,
            "Stream",
        );

        // Content
        let content_y = BROWSER_HEADER_HEIGHT + 95;
        let scale = PxScale::from(14.0);

        for (i, line) in JAVADOCS_CONTENT.iter().enumerate() {
            if (content_y + (i as u32 * 20)) > HEIGHT - 30 {
                break;
            }

            let color = if line.contains("===") {
                oracle_red
            } else if line.starts_with("  boolean")
                || line.starts_with("  void")
                || line.starts_with("  E ")
                || line.starts_with("  int")
            {
                TEXT_LINK
            } else if line.contains("Interface") {
                Rgba([0, 100, 0, 255])
            } else if line.contains("---") {
                Rgba([200, 200, 200, 255])
            } else if line.starts_with("    ") {
                TEXT_GRAY
            } else {
                TEXT_BROWSER
            };

            draw_text_mut(
                img,
                color,
                250,
                content_y as i32 + (i as i32 * 20),
                scale,
                &self.font,
                line,
            );
        }
    }

    fn draw_explorer(&self, img: &mut RgbaImage) {
        draw_filled_rect_mut(
            img,
            Rect::at(0, TAB_HEIGHT as i32)
                .of_size(EXPLORER_WIDTH, HEIGHT - TAB_HEIGHT - STATUS_BAR_HEIGHT),
            EXPLORER_BG,
        );

        let scale = PxScale::from(14.0);
        let y_start = TAB_HEIGHT as i32 + 10;

        draw_text_mut(img, TEXT_WHITE, 10, y_start, scale, &self.font, "EXPLORER");

        let files = [
            ("  src", true),
            ("    main", true),
            ("      java", true),
            ("        at.htl.exam", true),
            ("          Main.java", false),
            ("          Student.java", false),
            ("          StudentResource.java", false),
            ("      resources", true),
            ("        application.properties", false),
            ("    test", true),
            ("      java", true),
            ("  pom.xml", false),
            ("  run.sh", false),
        ];

        for (i, (name, is_folder)) in files.iter().enumerate() {
            let color = if *is_folder { TEXT_GRAY } else { TEXT_WHITE };
            draw_text_mut(
                img,
                color,
                10,
                y_start + 25 + (i as i32 * 18),
                scale,
                &self.font,
                name,
            );
        }
    }

    fn draw_tabs(&self, img: &mut RgbaImage) {
        draw_filled_rect_mut(img, Rect::at(0, 0).of_size(WIDTH, TAB_HEIGHT), TAB_BG);

        let tabs = [
            "Main.java",
            "Student.java",
            "application.properties",
            "pom.xml",
        ];
        let scale = PxScale::from(13.0);

        for (i, tab) in tabs.iter().enumerate() {
            let x = EXPLORER_WIDTH as i32 + (i as i32 * 150);
            let is_active = *tab == self.current_file.filename();

            let bg_color = if is_active { TAB_ACTIVE } else { TAB_BG };
            draw_filled_rect_mut(img, Rect::at(x, 0).of_size(148, TAB_HEIGHT), bg_color);

            draw_text_mut(img, TEXT_WHITE, x + 10, 10, scale, &self.font, tab);
        }
    }

    fn draw_editor(&self, img: &mut RgbaImage) {
        let editor_x = EXPLORER_WIDTH;
        let editor_y = TAB_HEIGHT;
        let editor_width = WIDTH - EXPLORER_WIDTH;
        let editor_height = HEIGHT - TAB_HEIGHT - TERMINAL_HEIGHT - STATUS_BAR_HEIGHT;

        draw_filled_rect_mut(
            img,
            Rect::at(editor_x as i32, editor_y as i32).of_size(editor_width, editor_height),
            EDITOR_BG,
        );

        draw_filled_rect_mut(
            img,
            Rect::at(editor_x as i32, editor_y as i32).of_size(LINE_NUM_WIDTH, editor_height),
            LINE_NUM_BG,
        );

        let scale = PxScale::from(14.0);
        let line_height = 20;

        let content = self.current_file.content();
        let visible_chars = self.typing_progress.min(content.len());
        let visible_content: String = content.chars().take(visible_chars).collect();

        for (i, line) in visible_content.lines().enumerate() {
            if i > 35 {
                break;
            }

            let y = editor_y as i32 + 5 + (i as i32 * line_height);

            draw_text_mut(
                img,
                TEXT_GRAY,
                editor_x as i32 + 5,
                y,
                scale,
                &self.font,
                &format!("{:>3}", i + 1),
            );

            self.draw_highlighted_line(
                img,
                editor_x as i32 + LINE_NUM_WIDTH as i32 + 10,
                y,
                line,
                scale,
            );
        }

        // Cursor
        let cursor_line = visible_content.lines().count().saturating_sub(1);
        let cursor_col = visible_content.lines().last().map(|l| l.len()).unwrap_or(0);
        let cursor_x = editor_x as i32 + LINE_NUM_WIDTH as i32 + 10 + (cursor_col as i32 * 8);
        let cursor_y = editor_y as i32 + 5 + (cursor_line as i32 * line_height);

        if self.frame_counter % 2 == 0 {
            draw_filled_rect_mut(img, Rect::at(cursor_x, cursor_y).of_size(2, 16), TEXT_WHITE);
        }
    }

    fn draw_highlighted_line(
        &self,
        img: &mut RgbaImage,
        x: i32,
        y: i32,
        line: &str,
        scale: PxScale,
    ) {
        let keywords = [
            "package",
            "import",
            "public",
            "private",
            "class",
            "void",
            "return",
            "new",
            "if",
            "else",
            "for",
            "while",
            "static",
            "final",
            "int",
            "long",
            "String",
            "boolean",
            "true",
            "false",
            "null",
            "this",
            "extends",
            "implements",
            "echo",
            "exit",
            "then",
            "fi",
        ];

        let trimmed = line.trim_start();

        let color =
            if trimmed.starts_with("//") || trimmed.starts_with('#') || trimmed.starts_with("--") {
                TEXT_COMMENT
            } else if trimmed.starts_with('@') {
                TEXT_TYPE
            } else if trimmed.starts_with('"') || trimmed.contains("\"") {
                TEXT_STRING
            } else if trimmed.starts_with('<') || trimmed.starts_with("</") {
                TEXT_TYPE
            } else if keywords.iter().any(|k| trimmed.starts_with(k)) {
                TEXT_KEYWORD
            } else if trimmed
                .chars()
                .next()
                .map(|c| c.is_ascii_digit())
                .unwrap_or(false)
            {
                TEXT_NUMBER
            } else {
                TEXT_WHITE
            };

        draw_text_mut(img, color, x, y, scale, &self.font, line);
    }

    fn draw_terminal(&self, img: &mut RgbaImage) {
        let term_y = HEIGHT - TERMINAL_HEIGHT - STATUS_BAR_HEIGHT;

        draw_filled_rect_mut(
            img,
            Rect::at(EXPLORER_WIDTH as i32, term_y as i32)
                .of_size(WIDTH - EXPLORER_WIDTH, TERMINAL_HEIGHT),
            TERMINAL_BG,
        );

        draw_filled_rect_mut(
            img,
            Rect::at(EXPLORER_WIDTH as i32, term_y as i32).of_size(WIDTH - EXPLORER_WIDTH, 25),
            TAB_BG,
        );

        let scale = PxScale::from(12.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            EXPLORER_WIDTH as i32 + 10,
            term_y as i32 + 5,
            scale,
            &self.font,
            "TERMINAL",
        );

        let scale = PxScale::from(13.0);
        for (i, line) in TERMINAL_OUTPUT.lines().take(8).enumerate() {
            draw_text_mut(
                img,
                TEXT_WHITE,
                EXPLORER_WIDTH as i32 + 10,
                term_y as i32 + 30 + (i as i32 * 18),
                scale,
                &self.font,
                line,
            );
        }
    }

    fn draw_status_bar(&self, img: &mut RgbaImage) {
        let y = HEIGHT - STATUS_BAR_HEIGHT;

        draw_filled_rect_mut(
            img,
            Rect::at(0, y as i32).of_size(WIDTH, STATUS_BAR_HEIGHT),
            STATUS_BAR,
        );

        let scale = PxScale::from(12.0);
        draw_text_mut(
            img,
            TEXT_WHITE,
            10,
            y as i32 + 5,
            scale,
            &self.font,
            "Ln 24, Col 45",
        );
        draw_text_mut(
            img,
            TEXT_WHITE,
            200,
            y as i32 + 5,
            scale,
            &self.font,
            "UTF-8",
        );
        draw_text_mut(
            img,
            TEXT_WHITE,
            300,
            y as i32 + 5,
            scale,
            &self.font,
            "Java",
        );
        draw_text_mut(
            img,
            TEXT_WHITE,
            (WIDTH - 200) as i32,
            y as i32 + 5,
            scale,
            &self.font,
            "main",
        );
    }
}
