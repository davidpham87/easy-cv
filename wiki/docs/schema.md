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

 ;; --- Experience ---
 ;; Professional experience entries.
 :experience/company {}
 :experience/role {}
 :experience/start-date {}
 :experience/end-date {} ;; nil or missing if current
 :experience/description {}
 :experience/location {}

 ;; --- Education ---
 ;; Educational background.
 :education/institution {}
 :education/degree {}
 :education/field {}
 :education/start-date {}
 :education/end-date {}
 :education/description {}

 ;; --- Skill ---
 ;; Skills and competencies.
 :skill/name {:db/unique :db.unique/identity}
 ;; :skill/category, :skill/level

 ;; --- Project ---
 ;; Side projects or significant work projects.
 :project/name {}
 :project/description {}
 :project/url {}
 :project/skills {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 ;; --- Hobby ---
 ;; Interests and hobbies.
 :hobby/name {}
 :hobby/description {}

 ;; --- CV Configuration ---
 ;; A specific CV version composed of selected facts.
 :cv/title {}
 :cv/person      {:db/valueType :db.type/ref}

 :cv/experiences {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/educations  {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/skills      {:db/cardinality :db.cardinality/many
                  :db/valueType   :db.type/ref}

 :cv/projects    {:db/cardinality :db.cardinality/many
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

### Experience
Represents a job or professional role.
- `:experience/company`: Name of the employer.
- `:experience/role`: Job title.
- `:experience/start-date`: Start date (e.g., "YYYY-MM").
- `:experience/end-date`: End date. If missing, implies "Present".
- `:experience/description`: Details of responsibilities and achievements.
- `:experience/location`: Location of the job.

### Education
Represents a degree or certification.
- `:education/institution`: Name of the school or university.
- `:education/degree`: Degree obtained (e.g., "B.Sc.").
- `:education/field`: Field of study (e.g., "Computer Science").
- `:education/start-date`: Start date.
- `:education/end-date`: Graduation date or expected graduation.

### Skill
Represents a specific skill.
- `:skill/name`: Name of the skill (e.g., "Clojure", "React"). Unique to avoid duplicates.
- `:skill/category`: Optional grouping (e.g., "Language", "Framework").

### Project
Represents a project.
- `:project/name`: Project title.
- `:project/description`: Description of the project.
- `:project/url`: Link to the project (e.g., GitHub, live demo).
- `:project/skills`: References to `:skill` entities used in this project.

### Hobby
Represents a hobby or interest.
- `:hobby/name`: Name of the hobby.
- `:hobby/description`: Short description.

### CV Configuration
Represents a specific configuration for a generated CV.
- `:cv/title`: Name of this CV version (e.g., "Frontend Developer CV").
- `:cv/person`: Reference to the `:person` entity.
- `:cv/experiences`: List of references to selected `:experience` entities.
- `:cv/educations`: List of references to selected `:education` entities.
- `:cv/skills`: List of references to selected `:skill` entities.
- `:cv/projects`: List of references to selected `:project` entities.
- `:cv/hobbies`: List of references to selected `:hobby` entities.
