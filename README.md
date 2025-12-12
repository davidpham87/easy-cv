# Easy CV: your CV as a collection of facts

## Why? 

Writing a good CV is time-consuming. 

The should be

* concise, but show you have the experience and the skills for a job
* be visually appealing, but standard to pass automatic CV screening
* personal but spotless

After spending way too much time writing my own CVs, I realize authoring a CV merges three responsibilities

1. Gathering your experience, education, skillset, and hobbies.
2. Select which facts are relevant for a job.
3. Styling your CV.

At each modification, we introduce a risk of making a mistake, placing a high burden on our minds.

However, what happens if we separate the three tasks?

1. We can invest time archiving our professional life and annotating facts such as education, hobbies, employers, skills or projects
2. Have a match between jobs and CV
3. Generate the structure of the CV with the facts, with predefined but configurable presets in multiple formats?

## Enter easy cv

This tool aims to make authoring CV *fun* and *fast* by helping you with step 1 and step 3.

Step 1 is the most time-consuming. This is why I decided to use the fastest method for entering data: a text editor and structured data.

Step 2 would require some form of LLMs, but it is out of scope for this open-source project (using LLMs requires either networking or compute power).

Given the choice of step 2, we can create a dynamic CV, and you can then have an interactive process of selecting which facts should be included or not. 

Once you are happy, you can play with the style.

We include: LaTeX, HTML, PDF, Markdown, data

We guarantee;

* Your privacy, I don't collect any data.
* You are in control; the data that you input is stored on your web browser, and you should duplicate it, your work might get wiped out otherwise.

## But AI?

You should use LLMs to help you provide the data!

I even wrote prompts to help LLMs to convert your CV into the relevant format for us, and you can use LLMs to choose which facts are relevant for a specific job.

## No Word/Doc format?!

First of all, learn LaTeX, this will save you time. Second, if you are dead set on Word or Docs, then you will have to rely on LLMs as Word and Docs do not provide an api to fill template.
