# Datascript Schema Design for Easy CV

This document outlines the Datascript schema design for the Easy CV project. The goal is to store CV data as a collection of facts (Experience, Education, Skills, etc.) and allow constructing specific CVs by selecting relevant facts.

## Schema Definition

```clojure
{
 ;; --- Core Identity ---
 ;; Unique identifier for all items to allow stable references and updates.
 :item/uuid {:db/unique :db.unique/identity}

 ;; --- Person ---
 ;; The user profile.
 :person/email   {:db/unique :db.unique/identity}
 ;; Other person attributes (implicit):
 ;; :person/name, :person/phone, :person/website, :person/summary, :person/location

 ;; --- Organization ---
 ;; Generic entity for Company, School, etc.
 :org/name     {}
 :org/type     {} ;; :company, :school
 :org/location {}
 :org/url      {}

 ;; --- History Item (Experience & Education) ---
 ;; Unified entity for professional experience and education.
 :history/organization {:db/valueType :db.type/ref}
 :history/type         {} ;; :work, :education
 :history/title        {} ;; Job Title or Degree
 :history/subtitle     {} ;; Field of Study (for education)
 :history/start-date   {}
 :history/end-date     {} ;; nil or missing if current
 :history/description  {}
 :history/location     {} ;; If different from Org location (e.g. remote)

 ;; --- Skill ---
 ;; Skills and competencies.
 :skill/name        {:db/unique :db.unique/identity}
 :skill/category    {} ;; e.g., "Language", "Framework"
 :skill/level       {} ;; e.g., "Expert", "Beginner"
 :skill/description {}

 ;; --- Project ---
 ;; Side projects or significant work projects.
 :project/name {}
 :project/description {}
 :project/url {}
 :project/skills {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 ;; --- Document ---
 ;; Documents linked in the CV.
 :document/name {}
 :document/url  {}
 :document/type {} ;; :publication, :reference, :diploma
 :document/date {}

 ;; --- Hobby ---
 ;; Interests and hobbies.
 :hobby/name {}
 :hobby/description {}

 ;; --- CV Configuration ---
 ;; A specific CV version composed of selected facts.
 :cv/title {}
 :cv/person      {:db/valueType :db.type/ref}

 :cv/history     {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/skills      {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/projects    {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/documents   {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/hobbies     {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}
}
```

## Entity Details

### Person
Represents the user.
- `:person/name`: Full name.
- `:person/email`: Email address (Unique).
- `:person/phone`: Phone number.
- `:person/website`: Personal website or portfolio URL.
- `:person/summary`: Professional summary or bio.
- `:person/location`: Current location (City, Country).

### Organization
Represents an institution or company.
- `:org/name`: Name of the organization (e.g., "Google", "MIT").
- `:org/type`: Type of organization. Values: `:company`, `:school`.
- `:org/location`: Headquarters or main location.
- `:org/url`: Website.

### History Item (Experience & Education)
Unified entity for timeline entries like jobs and degrees.
- `:history/type`: The type of entry. Values: `:work`, `:education`.
- `:history/organization`: Reference to an `:org` entity.
- `:history/title`: Main title.
    - For Work: Job Title (e.g., "Senior Engineer").
    - For Education: Degree (e.g., "B.Sc.").
- `:history/subtitle`: Secondary title info.
    - For Education: Field of study (e.g., "Computer Science").
- `:history/start-date`: Start date (e.g., "YYYY-MM").
- `:history/end-date`: End date. If missing, implies "Present".
- `:history/description`: Details of responsibilities, achievements, or coursework.
- `:history/location`: Specific location if different from Organization (e.g., Remote work).

### Skill
Represents a specific skill.
- `:skill/name`: Name of the skill (e.g., "Clojure", "React"). Unique.
- `:skill/category`: Grouping (e.g., "Language", "Framework").
- `:skill/level`: Proficiency level (e.g., "Expert", "Junior").
- `:skill/description`: Optional description or context for the skill.

### Project
Represents a project.
- `:project/name`: Project title.
- `:project/description`: Description of the project.
- `:project/url`: Link to the project (e.g., GitHub, live demo).
- `:project/skills`: References to `:skill` entities used in this project.

### Document
Represents an external document or resource.
- `:document/name`: Title of the document.
- `:document/url`: Link to the document.
- `:document/type`: Type of document. Values: `:publication`, `:reference`, `:diploma`.
- `:document/date`: Publication or issue date.

### Hobby
Represents a hobby or interest.
- `:hobby/name`: Name of the hobby.
- `:hobby/description`: Short description.

### CV Configuration
Represents a specific configuration for a generated CV.
- `:cv/title`: Name of this CV version (e.g., "Frontend Developer CV").
- `:cv/person`: Reference to the `:person` entity.
- `:cv/history`: List of references to `:history` entities (both Work and Education).
- `:cv/skills`: List of references to selected `:skill` entities.
- `:cv/projects`: List of references to selected `:project` entities.
- `:cv/documents`: List of references to selected `:document` entities.
- `:cv/hobbies`: List of references to selected `:hobby` entities.
